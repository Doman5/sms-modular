package com.domanski.smsmodular.identity.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.identity.dto.IdentityDtos.CreatedUserResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantResponse;
import com.domanski.smsmodular.identity.entity.TenantRole;
import com.domanski.smsmodular.tenancy.dto.CreateTenantRequest;
import com.domanski.smsmodular.tenancy.dto.TenantResponse;
import com.domanski.smsmodular.tenancy.service.TenantService;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

	private final TenantService tenants;
	private final RoleService roles;
	private final UserService users;
	private final AuditService audit;

	@Transactional
	public ProvisionTenantResponse provision(ProvisionTenantRequest request, AuditCallContext context) {
		TenantResponse tenant = tenants.create(new CreateTenantRequest(request.slug(), request.name(),
				request.timeZone(), request.locale()));
		TenantRole owner = roles.createOwner(tenant.id());
		CreatedUserResponse admin = users.createFirstAdmin(tenant.id(), request.adminEmail(),
				request.adminDisplayName(), owner.getId());
		audit.record(AuditCommand.success(tenant.id(), context, "TENANCY", "TENANT_PROVISIONED",
				"TENANT", tenant.id(), Map.of()));
		return new ProvisionTenantResponse(tenant, admin.user(), admin.temporaryPassword());
	}
}
