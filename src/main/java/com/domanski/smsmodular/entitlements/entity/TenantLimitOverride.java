package com.domanski.smsmodular.entitlements.entity;

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
@Table(name = "tenant_limit_overrides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantLimitOverride {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;
	@Column(name = "metric_code", nullable = false, length = 64)
	private String metricCode;
	@Column(nullable = false, length = 16)
	private String mode;
	@Column(name = "limit_value")
	private Long limitValue;
	@Column(name = "expires_at")
	private Instant expiresAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public TenantLimitOverride(UUID id, UUID tenantId, String metricCode, String mode,
			Long limitValue, Instant expiresAt, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.metricCode = metricCode;
		configure(mode, limitValue, expiresAt, now);
	}

	public void configure(String mode, Long limitValue, Instant expiresAt, Instant now) {
		this.mode = mode;
		this.limitValue = limitValue;
		this.expiresAt = expiresAt;
		this.updatedAt = now;
	}
}
