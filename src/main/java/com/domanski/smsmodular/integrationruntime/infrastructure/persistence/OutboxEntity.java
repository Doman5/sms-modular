package com.domanski.smsmodular.integrationruntime.infrastructure.persistence;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "outbox_messages")
class OutboxEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "topic", nullable = false, updatable = false, length = 128)
	private String topic;
	@Column(name = "aggregate_type", nullable = false, updatable = false, length = 128)
	private String aggregateType;
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	private UUID aggregateId;
	@Column(name = "payload_version", nullable = false, updatable = false)
	private int payloadVersion;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "jsonb")
	private String payload;
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 24)
	private OutboxStatus status;
	@Column(name = "attempt", nullable = false)
	private int attempt;
	@Column(name = "available_at", nullable = false)
	private Instant availableAt;
	@Column(name = "lease_owner", length = 128)
	private String leaseOwner;
	@Column(name = "lease_until")
	private Instant leaseUntil;
	@Column(name = "correlation_id", nullable = false, length = 64, updatable = false)
	private String correlationId;
	@Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
	private String idempotencyKey;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Column(name = "last_error_code", length = 64)
	private String lastErrorCode;

	protected OutboxEntity() {
	}

	static OutboxEntity from(OutboxMessage value) {
		OutboxEntity entity = new OutboxEntity();
		entity.id = value.id();
		entity.tenantId = value.tenantId().value();
		entity.topic = value.topic();
		entity.aggregateType = value.aggregateType();
		entity.aggregateId = value.aggregateId();
		entity.payloadVersion = value.payloadVersion();
		entity.payload = value.payload();
		entity.status = value.status();
		entity.attempt = value.attempt();
		entity.availableAt = value.availableAt();
		entity.leaseOwner = value.leaseOwner();
		entity.leaseUntil = value.leaseUntil();
		entity.correlationId = value.correlationId();
		entity.idempotencyKey = value.idempotencyKey();
		entity.createdAt = value.createdAt();
		entity.updatedAt = value.updatedAt();
		entity.lastErrorCode = value.lastErrorCode();
		return entity;
	}

	OutboxMessage toDomain() {
		return new OutboxMessage(
			id, TenantId.of(tenantId), topic, aggregateType, aggregateId, payloadVersion,
			payload, status, attempt, availableAt, leaseOwner, leaseUntil, correlationId,
			idempotencyKey, createdAt, updatedAt, lastErrorCode
		);
	}
}
