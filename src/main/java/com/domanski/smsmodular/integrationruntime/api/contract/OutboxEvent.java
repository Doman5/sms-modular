package com.domanski.smsmodular.integrationruntime.api.contract;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.Objects;
import java.util.UUID;





public record OutboxEvent(
	TenantId tenantId,
	String topic,
	String aggregateType,
	UUID aggregateId,
	int payloadVersion,
	String payload,
	String correlationId,
	String idempotencyKey
) {

	public OutboxEvent {
		Objects.requireNonNull(tenantId, "Event tenant is required");
		if (topic == null || topic.isBlank()) {
			throw new IllegalArgumentException("Event topic is required");
		}
		if (aggregateType == null || aggregateType.isBlank()) {
			throw new IllegalArgumentException("Event aggregate type is required");
		}
		Objects.requireNonNull(aggregateId, "Event aggregate ID is required");
		if (payloadVersion < 1) {
			throw new IllegalArgumentException("Event payload version must be positive");
		}
		if (payload == null || payload.isBlank() || payload.length() > 1_000_000) {
			throw new IllegalArgumentException("Event payload is missing or too large");
		}
		if (correlationId == null || !CorrelationContext.isSafe(correlationId)) {
			throw new IllegalArgumentException("Event correlation ID has an invalid format");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
			throw new IllegalArgumentException("Event idempotency key is required");
		}
	}
}
