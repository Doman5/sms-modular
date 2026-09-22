package com.domanski.smsmodular.integrationruntime.domain;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;






public record OutboxMessage(
	UUID id,
	TenantId tenantId,
	String topic,
	String aggregateType,
	UUID aggregateId,
	int payloadVersion,
	String payload,
	OutboxStatus status,
	int attempt,
	Instant availableAt,
	String leaseOwner,
	Instant leaseUntil,
	String correlationId,
	String idempotencyKey,
	Instant createdAt,
	Instant updatedAt,
	String lastErrorCode
) {

	public OutboxMessage {
		Objects.requireNonNull(id, "Outbox ID is required");
		Objects.requireNonNull(tenantId, "Outbox tenant is required");
		topic = requiredCode(topic, "Outbox topic", 128);
		aggregateType = requiredCode(aggregateType, "Outbox aggregate type", 128);
		Objects.requireNonNull(aggregateId, "Outbox aggregate ID is required");
		if (payloadVersion < 1) {
			throw new IllegalArgumentException("Outbox payload version must be positive");
		}
		if (payload == null || payload.isBlank()) {
			throw new IllegalArgumentException("Outbox payload is required");
		}
		if (payload.length() > 1_000_000) {
			throw new IllegalArgumentException("Outbox payload is too large");
		}
		Objects.requireNonNull(status, "Outbox status is required");
		if (attempt < 0) {
			throw new IllegalArgumentException("Outbox attempt cannot be negative");
		}
		Objects.requireNonNull(availableAt, "Outbox available time is required");
		if (correlationId == null || !CorrelationContext.isSafe(correlationId)) {
			throw new IllegalArgumentException("Outbox correlation ID has an invalid format");
		}
		idempotencyKey = requiredCode(idempotencyKey, "Outbox idempotency key", 255);
		Objects.requireNonNull(createdAt, "Outbox creation time is required");
		Objects.requireNonNull(updatedAt, "Outbox update time is required");
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("Outbox update time cannot precede creation time");
		}
		if (status == OutboxStatus.PROCESSING && (leaseOwner == null || leaseOwner.isBlank() || leaseUntil == null)) {
			throw new IllegalArgumentException("A processing message requires a lease owner and expiry");
		}
	}

	public boolean isTerminal() {
		return status == OutboxStatus.COMPLETED || status == OutboxStatus.DEAD_LETTER;
	}

	public boolean leaseExpired(Instant now) {
		return status == OutboxStatus.PROCESSING && leaseUntil != null && !leaseUntil.isAfter(now);
	}

	private static String requiredCode(String value, String label, int maxLength) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " is required");
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength || !normalized.matches("[A-Za-z0-9][A-Za-z0-9_.:/-]*")) {
			throw new IllegalArgumentException(label + " has an invalid format");
		}
		return normalized;
	}
}
