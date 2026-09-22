package com.domanski.smsmodular.integrationruntime.domain;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


public record InboxReceipt(
	UUID id,
	TenantId tenantId,
	String consumer,
	UUID eventId,
	Instant receivedAt,
	String correlationId
) {

	public InboxReceipt {
		Objects.requireNonNull(id, "Inbox receipt ID is required");
		Objects.requireNonNull(tenantId, "Inbox receipt tenant is required");
		if (consumer == null || consumer.isBlank() || consumer.length() > 128
			|| !consumer.matches("[A-Za-z0-9][A-Za-z0-9_.:/-]*")) {
			throw new IllegalArgumentException("Inbox consumer has an invalid format");
		}
		Objects.requireNonNull(eventId, "Inbox event ID is required");
		Objects.requireNonNull(receivedAt, "Inbox receipt time is required");
		if (correlationId == null || !CorrelationContext.isSafe(correlationId)) {
			throw new IllegalArgumentException("Inbox correlation ID has an invalid format");
		}
	}
}
