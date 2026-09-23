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
@Table(name = "tenant_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantSubscription {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;
	@Column(name = "plan_version_id", nullable = false)
	private UUID planVersionId;
	@Column(nullable = false, length = 16)
	private String status;
	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;
	@Column(name = "ends_at")
	private Instant endsAt;

	public TenantSubscription(UUID id, UUID tenantId, UUID planVersionId, Instant startsAt) {
		this.id = id;
		this.tenantId = tenantId;
		this.planVersionId = planVersionId;
		this.status = "ACTIVE";
		this.startsAt = startsAt;
	}
}
