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
@Table(name = "tenant_addons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantAddon {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;
	@Column(name = "module_key", nullable = false, length = 64)
	private String moduleKey;
	@Column(nullable = false, length = 16)
	private String status;
	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;
	@Column(name = "ends_at")
	private Instant endsAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public TenantAddon(UUID id, UUID tenantId, String moduleKey, Instant startsAt, Instant endsAt, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.moduleKey = moduleKey;
		configure(startsAt, endsAt, now);
	}

	public void configure(Instant startsAt, Instant endsAt, Instant now) {
		this.status = "ENABLED";
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.updatedAt = now;
	}

	public void disable(Instant now) {
		this.status = "DISABLED";
		this.updatedAt = now;
	}
}
