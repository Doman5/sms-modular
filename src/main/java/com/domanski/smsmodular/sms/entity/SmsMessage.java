package com.domanski.smsmodular.sms.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sms_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsMessage {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(nullable = false, length = 30, updatable = false)
	private String provider;
	@Column(name = "device_id", nullable = false, length = 120, updatable = false)
	private String deviceId;
	@Column(name = "event_id", nullable = false, length = 120, updatable = false)
	private String eventId;
	@Column(name = "message_id", nullable = false, length = 120, updatable = false)
	private String messageId;
	@Column(length = 30, updatable = false)
	private String recipient;
	@Column(name = "sim_number", updatable = false)
	private Integer simNumber;
	@Column(name = "sender_cipher")
	private String senderCipher;
	@Column(name = "content_cipher")
	private String contentCipher;
	@Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
	private String payloadHash;
	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;
	@Column(name = "employee_id")
	private UUID employeeId;
	@Column(nullable = false, length = 24)
	private String status;
	@Column(name = "review_reason", length = 40)
	private String reviewReason;
	@Column(length = 20)
	private String resolution;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Version
	@Column(nullable = false)
	private long version;

	public SmsMessage(UUID tenantId, String deviceId, String eventId, String messageId,
			String recipient, Integer simNumber, String senderCipher, String contentCipher,
			String payloadHash, Instant receivedAt, Instant now) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.provider = "SMS_GATE";
		this.deviceId = deviceId;
		this.eventId = eventId;
		this.messageId = messageId;
		this.recipient = recipient;
		this.simNumber = simNumber;
		this.senderCipher = senderCipher;
		this.contentCipher = contentCipher;
		this.payloadHash = payloadHash;
		this.receivedAt = receivedAt;
		this.status = "PENDING";
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void review(UUID employeeId, String reason, Instant now) {
		this.employeeId = employeeId;
		this.status = "REVIEW_REQUIRED";
		this.reviewReason = reason;
		this.updatedAt = now;
	}

	public void complete(UUID employeeId, String resolution, Instant now) {
		this.employeeId = employeeId;
		this.status = "COMPLETED";
		this.resolution = resolution;
		this.reviewReason = null;
		this.updatedAt = now;
	}

	public void dismiss(Instant now) {
		this.status = "DISMISSED";
		this.resolution = "DISMISS";
		this.reviewReason = null;
		this.updatedAt = now;
	}

	public void pending(Instant now) {
		this.status = "PENDING";
		this.reviewReason = null;
		this.updatedAt = now;
	}

	public void error(Instant now) {
		this.status = "ERROR";
		this.updatedAt = now;
	}

	public void expire(Instant now) {
		this.senderCipher = null;
		this.contentCipher = null;
		if (!status.equals("COMPLETED") && !status.equals("DISMISSED")) this.status = "EXPIRED";
		this.updatedAt = now;
	}
}
