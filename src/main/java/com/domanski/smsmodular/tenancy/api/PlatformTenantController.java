package com.domanski.smsmodular.tenancy.api;

import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleService;
import com.domanski.smsmodular.tenancy.application.TenantSettingsCommand;
import com.domanski.smsmodular.tenancy.application.TenantView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/platform/v1/tenants")
@Validated
public class PlatformTenantController {

	private final TenantLifecycleService service;

	public PlatformTenantController(TenantLifecycleService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_CREATE')")
	public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
		return TenantResponse.from(service.create(
			request.slug(),
			request.name(),
			request.timezone(),
			request.locale()
		));
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_READ')")
	public PageResponse<TenantResponse> list(
		@PageableDefault(size = 25, sort = "createdAt") Pageable pageable
	) {
		Page<TenantView> page = service.list(pageable);
		return PageResponse.from(page.map(TenantResponse::from));
	}

	@GetMapping("/{tenantId}")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_READ')")
	public TenantResponse get(@PathVariable UUID tenantId) {
		return TenantResponse.from(service.get(TenantId.of(tenantId)));
	}

	@PatchMapping("/{tenantId}")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_UPDATE')")
	public TenantResponse update(
		@PathVariable UUID tenantId,
		@Valid @RequestBody TenantSettingsPatchRequest request
	) {
		return TenantResponse.from(service.update(
			TenantId.of(tenantId),
			new TenantSettingsCommand(request.name(), request.timezone(), request.locale())
		));
	}

	@PostMapping("/{tenantId}/suspend")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_SUSPEND')")
	public TenantResponse suspend(@PathVariable UUID tenantId) {
		return TenantResponse.from(service.suspend(TenantId.of(tenantId)));
	}

	@PostMapping("/{tenantId}/activate")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_ACTIVATE')")
	public TenantResponse activate(@PathVariable UUID tenantId) {
		return TenantResponse.from(service.activate(TenantId.of(tenantId)));
	}

	@PostMapping("/{tenantId}/close")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_CLOSE')")
	public TenantResponse close(@PathVariable UUID tenantId) {
		return TenantResponse.from(service.close(TenantId.of(tenantId)));
	}
}
