package com.domanski.smsmodular.identity.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.identity.dto.IdentityDtos.CreateRoleRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.RoleResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UpdateRoleRequest;
import com.domanski.smsmodular.identity.entity.AccountStatus;
import com.domanski.smsmodular.identity.entity.RolePermission;
import com.domanski.smsmodular.identity.entity.TenantRole;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.repository.RolePermissionRepository;
import com.domanski.smsmodular.identity.repository.TenantRoleRepository;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.tenancy.service.TenantService;

@Service
@RequiredArgsConstructor
public class RoleService {

	private final TenantRoleRepository roles;
	private final RolePermissionRepository rolePermissions;
	private final UserAccountRepository users;
	private final TenantService tenants;
	private final IdentityPolicy policy;
	private final Clock clock;
	private final AuditService audit;

	@Transactional
	public TenantRole createOwner(UUID tenantId) {
		TenantRole role = roles.save(new TenantRole(UUID.randomUUID(), tenantId, "OWNER", "Administrator", clock.instant()));
		Set<String> permissions = PermissionCatalog.TENANT_PERMISSIONS;
		rolePermissions.saveAll(permissions.stream()
				.map(code -> new RolePermission(UUID.randomUUID(), tenantId, role.getId(), code)).toList());
		return role;
	}

	@Transactional(readOnly = true)
	public List<RoleResponse> list(UUID tenantId) {
		return roles.findByTenantIdOrderByNameAsc(tenantId).stream()
				.map(role -> RoleResponse.from(role, permissions(tenantId, role.getId()))).toList();
	}

	@Transactional
	public RoleResponse create(UUID tenantId, CreateRoleRequest request, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		String code = policy.roleCode(request.code());
		String name = policy.roleName(request.name());
		Set<String> permissions = validPermissions(request.permissions());
		if (roles.findByTenantIdAndCode(tenantId, code).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "ROLE_CODE_CONFLICT", "Role code is already in use");
		}
		TenantRole role = roles.save(new TenantRole(UUID.randomUUID(), tenantId, code, name, clock.instant()));
		writePermissions(tenantId, role.getId(), permissions);
		audit.record(AuditCommand.success(tenantId, context, "IDENTITY", "ROLE_CREATED",
				"ROLE", role.getId(), Map.of("addedPermissions", permissions.stream().sorted().toList())));
		return RoleResponse.from(role, permissions);
	}

	@Transactional
	public RoleResponse update(UUID tenantId, UUID roleId, UpdateRoleRequest request, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		TenantRole role = require(tenantId, roleId);
		String name = policy.roleName(request.name());
		Set<String> permissions = validPermissions(request.permissions());
		Set<String> previous = permissions(tenantId, roleId);
		boolean nameChanged = !role.getName().equals(name);
		if (!nameChanged && previous.equals(permissions)) {
			return RoleResponse.from(role, permissions);
		}
		ensureAdminRemains(tenantId, roleId, permissions, null);
		role.setName(name);
		role.setUpdatedAt(clock.instant());
		rolePermissions.deleteByTenantIdAndRoleId(tenantId, roleId);
		rolePermissions.flush();
		writePermissions(tenantId, roleId, permissions);
		Set<String> added = new HashSet<>(permissions);
		added.removeAll(previous);
		Set<String> removed = new HashSet<>(previous);
		removed.removeAll(permissions);
		var changed = new ArrayList<String>();
		if (nameChanged) changed.add("NAME");
		audit.record(AuditCommand.success(tenantId, context, "IDENTITY", "ROLE_UPDATED", "ROLE", roleId,
				Map.of("changedFields", changed, "addedPermissions", added.stream().sorted().toList(),
						"removedPermissions", removed.stream().sorted().toList())));
		return RoleResponse.from(role, permissions);
	}

	@Transactional
	public void delete(UUID tenantId, UUID roleId, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		TenantRole role = require(tenantId, roleId);
		if (users.existsByTenantIdAndRoleId(tenantId, roleId)) {
			throw new ApiException(HttpStatus.CONFLICT, "ROLE_IN_USE", "Role is assigned to users");
		}
		rolePermissions.deleteByTenantIdAndRoleId(tenantId, roleId);
		roles.delete(role);
		audit.record(AuditCommand.success(tenantId, context, "IDENTITY", "ROLE_DELETED",
				"ROLE", roleId, Map.of()));
	}

	@Transactional(readOnly = true)
	public TenantRole require(UUID tenantId, UUID roleId) {
		return roles.findByTenantIdAndId(tenantId, roleId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Role was not found"));
	}

	@Transactional(readOnly = true)
	public Set<String> permissions(UUID tenantId, UUID roleId) {
		return rolePermissions.findByTenantIdAndRoleId(tenantId, roleId).stream()
				.map(RolePermission::getPermissionCode).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public void ensureAdminRemains(UUID tenantId, UUID changedRoleId, Set<String> newPermissions,
			UserAccount changedUser) {
		boolean remains = users.findByTenantIdAndStatus(tenantId, AccountStatus.ACTIVE).stream()
				.anyMatch(user -> {
					if (changedUser != null && user.getId().equals(changedUser.getId())
							&& changedUser.getStatus() != AccountStatus.ACTIVE) {
						return false;
					}
					UUID roleId = changedUser != null && user.getId().equals(changedUser.getId())
							? changedUser.getRoleId() : user.getRoleId();
					Set<String> effective = roleId.equals(changedRoleId) ? newPermissions : permissions(tenantId, roleId);
					return effective.contains(PermissionCatalog.USER_MANAGE)
							&& effective.contains(PermissionCatalog.ROLE_MANAGE);
				});
		if (!remains) {
			throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED", "At least one active administrator is required");
		}
	}

	private Set<String> validPermissions(Set<String> value) {
		if (value == null || !PermissionCatalog.TENANT_PERMISSIONS.containsAll(value)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PERMISSION_INVALID", "Role permissions are invalid");
		}
		return Set.copyOf(new HashSet<>(value));
	}

	private void writePermissions(UUID tenantId, UUID roleId, Set<String> permissions) {
		rolePermissions.saveAll(permissions.stream()
				.map(code -> new RolePermission(UUID.randomUUID(), tenantId, roleId, code)).toList());
	}
}
