package com.domanski.smsmodular.tenancy.dto;

import java.time.Instant;
import java.util.UUID;

import com.domanski.smsmodular.tenancy.entity.Tenant;
import com.domanski.smsmodular.tenancy.api.TenantStatus;

public record TenantResponse(
		UUID id,
		String slug,
		String name,
		TenantStatus status,
		String timeZone,
		String locale,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt) {

	public static TenantResponse from(Tenant tenant) {
		return new TenantResponse(
				tenant.getId(),
				tenant.getSlug(),
				tenant.getName(),
				tenant.getStatus(),
				tenant.getTimeZone(),
				tenant.getLocale(),
				tenant.getCreatedAt(),
				tenant.getUpdatedAt(),
				tenant.getClosedAt());
	}
}
