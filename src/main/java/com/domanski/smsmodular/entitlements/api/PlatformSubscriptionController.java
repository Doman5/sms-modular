package com.domanski.smsmodular.entitlements.api;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.SubscriptionView;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateAddonRequest;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateLimitRequest;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.identity.service.UserService;
import com.domanski.smsmodular.usage.service.UsageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/platform/v1/tenants/{tenantId}/subscription")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PlatformSubscriptionController {
	private final EntitlementService entitlements;
	private final UsageService usage;
	private final UserService users;
	private final EmployeeService employees;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_READ')")
	public SubscriptionView get(@PathVariable UUID tenantId) {
		return entitlements.view(tenantId, usage.activeUsers(tenantId, users.activeCount(tenantId)),
				usage.activeEmployees(tenantId, employees.activeCount(tenantId)));
	}

	@PutMapping("/addons/{key}")
	@PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_MANAGE')")
	public SubscriptionView addon(@PathVariable UUID tenantId, @PathVariable String key,
			@Valid @RequestBody UpdateAddonRequest request, HttpServletRequest servletRequest) {
		entitlements.updateAddon(tenantId, key, request,
				AuditCallContext.platformUser(current.principal().id(), servletRequest));
		return get(tenantId);
	}

	@PutMapping("/limits/{metric}")
	@PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTION_MANAGE')")
	public SubscriptionView limit(@PathVariable UUID tenantId, @PathVariable String metric,
			@Valid @RequestBody UpdateLimitRequest request, HttpServletRequest servletRequest) {
		entitlements.updateLimit(tenantId, metric, request,
				AuditCallContext.platformUser(current.principal().id(), servletRequest));
		return get(tenantId);
	}
}
