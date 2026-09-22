package com.domanski.smsmodular.integrationruntime.infrastructure.persistence;

import com.domanski.smsmodular.integrationruntime.domain.InboxReceipt;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_receipts")
class InboxReceiptEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "consumer", nullable = false, updatable = false, length = 128)
	private String consumer;
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;
	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;
	@Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
	private String correlationId;

	protected InboxReceiptEntity() {
	}

	static InboxReceiptEntity from(InboxReceipt value) {
		InboxReceiptEntity entity = new InboxReceiptEntity();
		entity.id = value.id();
		entity.tenantId = value.tenantId().value();
		entity.consumer = value.consumer();
		entity.eventId = value.eventId();
		entity.receivedAt = value.receivedAt();
		entity.correlationId = value.correlationId();
		return entity;
	}

	InboxReceipt toDomain() {
		return new InboxReceipt(id, TenantId.of(tenantId), consumer, eventId, receivedAt, correlationId);
	}
}
