package com.domanski.smsmodular.tenancy.api;

import com.domanski.smsmodular.tenancy.application.TenantView;
import java.time.Instant;
import java.util.UUID;


public record TenantResponse(
	UUID id,
	String slug,
	String name,
	String status,
	String timezone,
	String locale,
	Instant createdAt,
	Instant updatedAt,
	Instant closedAt
) {

	public static TenantResponse from(TenantView view) {
		return new TenantResponse(
			view.id().value(),
			view.slug(),
			view.name(),
			view.status().name(),
			view.timezone(),
			view.locale(),
			view.createdAt(),
			view.updatedAt(),
			view.closedAt()
		);
	}
}
