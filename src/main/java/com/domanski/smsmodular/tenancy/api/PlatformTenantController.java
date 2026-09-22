package com.domanski.smsmodular.tenancy.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.identity.security.CurrentIdentity;

import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantResponse;
import com.domanski.smsmodular.identity.service.TenantProvisioningService;
import com.domanski.smsmodular.tenancy.dto.TenantResponse;
import com.domanski.smsmodular.tenancy.dto.UpdateTenantRequest;
import com.domanski.smsmodular.tenancy.service.TenantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/platform/v1/tenants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PlatformTenantController {

	private final TenantService tenants;
	private final TenantProvisioningService provisioning;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_READ')")
	public PageResponse<TenantResponse> list(@RequestParam(required = false) TenantStatus status, Pageable pageable) {
		return tenants.list(status, pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_READ')")
	public TenantResponse get(@PathVariable UUID id) {
		return tenants.get(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public ResponseEntity<ProvisionTenantResponse> create(@Valid @RequestBody ProvisionTenantRequest request,
			HttpServletRequest servletRequest) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(provisioning.provision(request,
				AuditCallContext.platformUser(current.principal().id(), servletRequest)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public TenantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request,
			HttpServletRequest servletRequest) {
		return tenants.update(id, request, AuditCallContext.platformUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/suspend")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public TenantResponse suspend(@PathVariable UUID id, HttpServletRequest servletRequest) {
		return tenants.suspend(id, AuditCallContext.platformUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/activate")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public TenantResponse activate(@PathVariable UUID id, HttpServletRequest servletRequest) {
		return tenants.activate(id, AuditCallContext.platformUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/close")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public TenantResponse close(@PathVariable UUID id, HttpServletRequest servletRequest) {
		return tenants.close(id, AuditCallContext.platformUser(current.principal().id(), servletRequest));
	}
}
