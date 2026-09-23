package com.domanski.smsmodular.entitlements.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.SubscriptionView;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.identity.service.UserService;
import com.domanski.smsmodular.usage.service.UsageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/subscription")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TenantSubscriptionController {
	private final EntitlementService entitlements;
	private final UsageService usage;
	private final UserService users;
	private final EmployeeService employees;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
	public SubscriptionView get() {
		var tenantId = current.tenantId();
		return entitlements.view(tenantId, usage.activeUsers(tenantId, users.activeCount(tenantId)),
				usage.activeEmployees(tenantId, employees.activeCount(tenantId)));
	}
}
