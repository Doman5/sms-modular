package com.domanski.smsmodular.tenancy.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.domanski.smsmodular.tenancy.api.TenantStatus;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "slug", nullable = false, length = 64, unique = true)
	private String slug;

	@Column(name = "name", nullable = false, length = 160)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private TenantStatus status;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@Column(name = "locale", nullable = false, length = 35)
	private String locale;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	public Tenant(UUID id, String slug, String name, TenantStatus status, String timeZone,
			String locale, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.slug = slug;
		this.name = name;
		this.status = status;
		this.timeZone = timeZone;
		this.locale = locale;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public void updateDetails(String name, String timeZone, String locale, Instant updatedAt) {
		this.name = name;
		this.timeZone = timeZone;
		this.locale = locale;
		this.updatedAt = updatedAt;
	}

	public void setStatus(TenantStatus status, Instant updatedAt) {
		this.status = status;
		this.updatedAt = updatedAt;
	}

	public void close(Instant closedAt) {
		this.status = TenantStatus.CLOSED;
		this.closedAt = closedAt;
		this.updatedAt = closedAt;
	}
}
