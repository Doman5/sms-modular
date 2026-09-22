package com.domanski.smsmodular.tenancy.api;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.domanski.smsmodular.audit.api.AuditCallContext;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.tenancy.dto.TenantResponse;
import com.domanski.smsmodular.tenancy.dto.UpdateTenantRequest;
import com.domanski.smsmodular.tenancy.service.TenantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/tenant/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TenantSettingsController {

	private final TenantService tenants;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('TENANT_READ')")
	public TenantResponse get() {
		return tenants.get(current.tenantId());
	}

	@PutMapping
	@PreAuthorize("hasAuthority('TENANT_EDIT')")
	public TenantResponse update(@Valid @RequestBody UpdateTenantRequest request, HttpServletRequest servletRequest) {
		return tenants.update(current.tenantId(), request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}
}
