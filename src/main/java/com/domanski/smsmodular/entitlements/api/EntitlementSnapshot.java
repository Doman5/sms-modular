package com.domanski.smsmodular.entitlements.api;

import java.time.Instant;
import java.util.Set;

public record EntitlementSnapshot(Set<String> capabilities, String activeUserLimitMode,
		Long activeUserLimit, String activeEmployeeLimitMode, Long activeEmployeeLimit, Instant validUntil) {
}
