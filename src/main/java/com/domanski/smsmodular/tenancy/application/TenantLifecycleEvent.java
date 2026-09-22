package com.domanski.smsmodular.tenancy.application;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.UUID;


public record TenantLifecycleEvent(
	TenantId tenantId,
	Action action,
	Instant occurredAt,
	String correlationId,
	int contractVersion,
	UUID eventId,
	String idempotencyKey
) {

	public TenantLifecycleEvent {
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = CorrelationContext.currentOrGenerate();
		}
		if (contractVersion <= 0) {
			throw new IllegalArgumentException("Event contract version must be positive");
		}
		if (eventId == null) {
			eventId = UUID.randomUUID();
		}
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			idempotencyKey = eventId.toString();
		}
	}

	public enum Action {
	CREATED,
	SETTINGS_UPDATED,
	SUSPENDED,
	ACTIVATED,
	CLOSED
	}
}
