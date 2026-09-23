package com.domanski.smsmodular.identity.service;

import java.time.Clock;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.CreateUserRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.CreatedUserResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.TemporaryPasswordResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UpdateUserRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UserResponse;
import com.domanski.smsmodular.identity.entity.AccountStatus;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.tenancy.service.TenantService;
import com.domanski.smsmodular.usage.service.UsageService;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserAccountRepository users;
	private final RoleService roles;
	private final TenantService tenants;
	private final IdentityPolicy policy;
	private final PasswordEncoder passwords;
	private final Clock clock;
	private final AuditService audit;
	private final UsageService usage;

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(UUID tenantId, Pageable pageable) {
		return PageResponse.from(users.findByTenantId(tenantId, pageable).map(UserResponse::from));
	}

	@Transactional
	public CreatedUserResponse create(UUID tenantId, CreateUserRequest request, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		CreatedUserResponse created = createInternal(tenantId, request);
		audit.record(AuditCommand.success(tenantId, context, "IDENTITY", "USER_CREATED",
				"USER", created.user().id(), Map.of()));
		return created;
	}

	public CreatedUserResponse createFirstAdmin(UUID tenantId, String email, String displayName, UUID roleId) {
		return createInternal(tenantId, new CreateUserRequest(email, displayName, roleId));
	}

	private CreatedUserResponse createInternal(UUID tenantId, CreateUserRequest request) {
		String email = policy.email(request.email());
		String name = policy.name(request.displayName());
		roles.require(tenantId, request.roleId());
		if (users.findByNormalizedEmail(email).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_CONFLICT", "Email is already in use");
		}
		usage.requireAdditionalActiveUser(tenantId, activeCount(tenantId));
		String temporaryPassword = policy.temporaryPassword();
		UserAccount user = new UserAccount(UUID.randomUUID(), tenantId, request.roleId(), email,
				name, passwords.encode(temporaryPassword), clock.instant());
		try {
			users.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_CONFLICT", "Email is already in use");
		}
		return new CreatedUserResponse(UserResponse.from(user), temporaryPassword);
	}

	@Transactional
	public UserResponse update(UUID tenantId, UUID id, UpdateUserRequest request, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		UserAccount user = require(tenantId, id);
		String name = policy.name(request.displayName());
		roles.require(tenantId, request.roleId());
		List<String> changed = new ArrayList<>();
		if (!user.getDisplayName().equals(name)) changed.add("DISPLAY_NAME");
		if (!user.getRoleId().equals(request.roleId())) changed.add("ROLE");
		if (!changed.isEmpty()) {
			user.setDisplayName(name);
			user.setRoleId(request.roleId());
			roles.ensureAdminRemains(tenantId, request.roleId(), roles.permissions(tenantId, request.roleId()), user);
			user.setSessionVersion(user.getSessionVersion() + 1);
			user.setUpdatedAt(clock.instant());
			audit.record(AuditCommand.success(tenantId, context, "IDENTITY",
					"USER_UPDATED", "USER", id, Map.of("changedFields", changed)));
		}
		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse setStatus(UUID tenantId, UUID id, AccountStatus status, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		UserAccount user = require(tenantId, id);
		String previous = user.getStatus().name();
		if (!previous.equals(status.name())) {
			if (status == AccountStatus.ACTIVE) usage.requireAdditionalActiveUser(tenantId, activeCount(tenantId));
			user.setStatus(status);
			roles.ensureAdminRemains(tenantId, user.getRoleId(), roles.permissions(tenantId, user.getRoleId()), user);
			user.setSessionVersion(user.getSessionVersion() + 1);
			user.setUpdatedAt(clock.instant());
			audit.record(AuditCommand.success(tenantId, context, "IDENTITY",
					"USER_STATUS_CHANGED", "USER", id, Map.of("fromStatus", previous,
							"toStatus", status.name())));
		}
		return UserResponse.from(user);
	}

	@Transactional
	public TemporaryPasswordResponse resetPassword(UUID tenantId, UUID id, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		UserAccount user = require(tenantId, id);
		String temporaryPassword = policy.temporaryPassword();
		user.setPasswordHash(passwords.encode(temporaryPassword));
		user.setMustChangePassword(true);
		user.setSessionVersion(user.getSessionVersion() + 1);
		user.setFailedAttempts(0);
		user.setFirstFailedAt(null);
		user.setLockedUntil(null);
		user.setUpdatedAt(clock.instant());
		audit.record(AuditCommand.success(tenantId, context, "IDENTITY", "USER_PASSWORD_RESET",
				"USER", id, Map.of()));
		return new TemporaryPasswordResponse(temporaryPassword);
	}

	@Transactional(readOnly = true)
	public UserAccount require(UUID tenantId, UUID id) {
		return users.findByTenantIdAndId(tenantId, id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
	}

	@Transactional(readOnly = true)
	public long activeCount(UUID tenantId) {
		return users.countByTenantIdAndStatus(tenantId, AccountStatus.ACTIVE);
	}
}
