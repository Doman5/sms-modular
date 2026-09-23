package com.domanski.smsmodular.integrationruntime.entity;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "sms_message_id", nullable = false, updatable = false)
	private UUID smsMessageId;
	@Column(nullable = false, updatable = false, length = 40)
	private String topic;
	@Column(nullable = false, length = 20)
	private String status;
	@Column(nullable = false)
	private int attempt;
	@Column(name = "available_at", nullable = false)
	private Instant availableAt;
	@Column(name = "lease_owner", length = 100)
	private String leaseOwner;
	@Column(name = "lease_until")
	private Instant leaseUntil;
	@Column(name = "error_code", length = 60)
	private String errorCode;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public OutboxMessage(UUID tenantId, UUID smsMessageId, Instant now) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.smsMessageId = smsMessageId;
		this.topic = "SMS_PROCESS";
		this.status = "PENDING";
		this.availableAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void lease(String owner, Instant now) {
		this.status = "PROCESSING";
		this.attempt++;
		this.leaseOwner = owner;
		this.leaseUntil = now.plusSeconds(120);
		this.updatedAt = now;
	}

	public boolean ownedBy(String owner) {
		return status.equals("PROCESSING") && owner.equals(leaseOwner);
	}

	public void complete(Instant now) {
		this.status = "DONE";
		this.leaseOwner = null;
		this.leaseUntil = null;
		this.errorCode = null;
		this.updatedAt = now;
	}

	public void fail(String code, Instant now) {
		this.status = attempt >= 5 ? "DEAD_LETTER" : "RETRY_WAIT";
		long base = switch (attempt) {
			case 1 -> 60;
			case 2 -> 300;
			case 3 -> 900;
			default -> 3600;
		};
		this.availableAt = now.plusSeconds(base + ThreadLocalRandom.current().nextLong(base / 10 + 1));
		this.leaseOwner = null;
		this.leaseUntil = null;
		this.errorCode = code;
		this.updatedAt = now;
	}
}
