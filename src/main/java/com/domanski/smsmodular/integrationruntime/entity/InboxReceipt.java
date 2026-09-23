package com.domanski.smsmodular.integrationruntime.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inbox_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxReceipt {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "sms_message_id", nullable = false, updatable = false)
	private UUID smsMessageId;
	@Column(nullable = false, updatable = false, length = 30)
	private String provider;
	@Column(name = "device_id", nullable = false, updatable = false, length = 120)
	private String deviceId;
	@Column(name = "event_id", nullable = false, updatable = false, length = 120)
	private String eventId;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public InboxReceipt(UUID tenantId, UUID smsMessageId, String provider, String deviceId,
			String eventId, Instant now) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.smsMessageId = smsMessageId;
		this.provider = provider;
		this.deviceId = deviceId;
		this.eventId = eventId;
		this.createdAt = now;
	}
}
