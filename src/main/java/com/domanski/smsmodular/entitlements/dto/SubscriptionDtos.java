package com.domanski.smsmodular.entitlements.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import com.domanski.smsmodular.usage.api.UsageSnapshot;

public final class SubscriptionDtos {
	private SubscriptionDtos() {
	}

	public record AddonView(String key, String type, String availability, String status,
			Instant startsAt, Instant endsAt, String dependsOn) {
	}

	public record SubscriptionView(UUID tenantId, String planCode, int planVersion,
			Set<String> capabilities, List<AddonView> modules, UsageSnapshot usage,
			LimitOverrideView limitOverride, UsageSnapshot employeeUsage,
			LimitOverrideView employeeLimitOverride, Instant validUntil) {
	}

	public record LimitOverrideView(String mode, Long value, Instant expiresAt) {
	}

	public record UpdateAddonRequest(@NotNull Boolean enabled, Instant startsAt, Instant endsAt) {
	}

	public record UpdateLimitRequest(@NotNull String mode, Long value, Instant expiresAt) {
	}
}
