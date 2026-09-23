package com.domanski.smsmodular.sms.entity;

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
@Table(name = "sms_routes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsRoute {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "device_id", nullable = false, length = 120)
	private String deviceId;
	@Column(length = 30)
	private String recipient;
	@Column(name = "sim_number")
	private Integer simNumber;
	@Column(nullable = false)
	private boolean active;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public SmsRoute(UUID tenantId, String deviceId, String recipient, Integer simNumber, Instant now) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.deviceId = deviceId;
		this.recipient = recipient;
		this.simNumber = simNumber;
		this.active = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void deactivate(Instant now) {
		this.active = false;
		this.updatedAt = now;
	}
}
