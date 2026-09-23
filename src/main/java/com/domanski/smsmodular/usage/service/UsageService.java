package com.domanski.smsmodular.usage.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.entitlements.api.EntitlementSnapshot;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.usage.api.UsageSnapshot;

@Service
@RequiredArgsConstructor
public class UsageService {
	private final EntitlementService entitlements;

	public UsageSnapshot activeUsers(UUID tenantId, long used) {
		EntitlementSnapshot entitlement = entitlements.snapshot(tenantId);
		Long limit = entitlement.activeUserLimit();
		Long remaining = limit == null ? null : Math.max(0, limit - used);
		return new UsageSnapshot(EntitlementService.ACTIVE_USERS, used,
				entitlement.activeUserLimitMode(), limit, remaining);
	}

	public void requireAdditionalActiveUser(UUID tenantId, long used) {
		UsageSnapshot snapshot = activeUsers(tenantId, used);
		if (snapshot.limit() != null && used >= snapshot.limit()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "LIMIT_EXCEEDED", "Active user limit is exceeded");
		}
	}

	public UsageSnapshot activeEmployees(UUID tenantId, long used) {
		EntitlementSnapshot entitlement = entitlements.snapshot(tenantId);
		Long limit = entitlement.activeEmployeeLimit();
		Long remaining = limit == null ? null : Math.max(0, limit - used);
		return new UsageSnapshot(EntitlementService.ACTIVE_EMPLOYEES, used,
				entitlement.activeEmployeeLimitMode(), limit, remaining);
	}

	public void requireAdditionalActiveEmployee(UUID tenantId, long used) {
		UsageSnapshot snapshot = activeEmployees(tenantId, used);
		if (snapshot.limit() != null && used >= snapshot.limit()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "EMPLOYEE_LIMIT_EXCEEDED", "Active employee limit is exceeded");
		}
	}
}
