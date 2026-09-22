package com.domanski.smsmodular.tenancy.api;

import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleService;
import com.domanski.smsmodular.tenancy.application.TenantSettingsCommand;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/tenant")
public class TenantController {

	private final TenantLifecycleService service;

	public TenantController(TenantLifecycleService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public TenantResponse get() {
		
		
		TenantContext.require();
		return TenantResponse.from(service.getCurrent());
	}

	@PatchMapping
	@PreAuthorize("hasAuthority('TENANT_SETTINGS_EDIT')")
	public TenantResponse update(@Valid @RequestBody TenantSettingsPatchRequest request) {
		TenantContext.require();
		return TenantResponse.from(service.updateCurrent(
			new TenantSettingsCommand(request.name(), request.timezone(), request.locale())
		));
	}
}
