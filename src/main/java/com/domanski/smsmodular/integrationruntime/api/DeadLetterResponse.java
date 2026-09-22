package com.domanski.smsmodular.integrationruntime.api;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import java.time.Instant;
import java.util.UUID;


public record DeadLetterResponse(
	UUID id,
	UUID tenantId,
	String topic,
	String aggregateType,
	UUID aggregateId,
	int payloadVersion,
	String status,
	int attempt,
	Instant availableAt,
	String correlationId,
	String idempotencyKey,
	Instant createdAt,
	Instant updatedAt,
	String errorCode
) {

	public static DeadLetterResponse from(OutboxMessage message) {
		return new DeadLetterResponse(
			message.id(),
			message.tenantId().value(),
			message.topic(),
			message.aggregateType(),
			message.aggregateId(),
			message.payloadVersion(),
			message.status().name(),
			message.attempt(),
			message.availableAt(),
			message.correlationId(),
			message.idempotencyKey(),
			message.createdAt(),
			message.updatedAt(),
			message.lastErrorCode()
		);
	}
}
