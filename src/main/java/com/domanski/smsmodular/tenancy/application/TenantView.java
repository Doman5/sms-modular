package com.domanski.smsmodular.tenancy.application;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.domain.TenantStatus;
import java.time.Instant;


public record TenantView(
	TenantId id,
	String slug,
	String name,
	TenantStatus status,
	String timezone,
	String locale,
	Instant createdAt,
	Instant updatedAt,
	Instant closedAt
) {
}
