package com.domanski.smsmodular.tenancy.infrastructure.persistence;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import com.domanski.smsmodular.tenancy.domain.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "tenants", uniqueConstraints = @UniqueConstraint(
	name = "uq_tenants_slug",
	columnNames = "slug"
))
class TenantEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "slug", nullable = false, updatable = false, length = 64)
	private String slug;

	@Column(name = "name", nullable = false, length = 255)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private TenantStatus status;

	@Column(name = "timezone", nullable = false, length = 64)
	private String timezone;

	@Column(name = "locale", nullable = false, length = 32)
	private String locale;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected TenantEntity() {
	}

	static TenantEntity from(Tenant tenant) {
		TenantEntity entity = new TenantEntity();
		entity.id = tenant.id().value();
		entity.slug = tenant.slug();
		entity.copyMutableState(tenant);
		entity.createdAt = tenant.createdAt();
		return entity;
	}

	void copyMutableState(Tenant tenant) {
		if (id != null && !id.equals(tenant.id().value())) {
			throw new IllegalArgumentException("Tenant ID cannot change");
		}
		if (slug != null && !slug.equals(tenant.slug())) {
			throw new IllegalArgumentException("Tenant slug cannot change");
		}
		this.id = tenant.id().value();
		this.slug = tenant.slug();
		this.name = tenant.name();
		this.status = tenant.status();
		this.timezone = tenant.timezone();
		this.locale = tenant.locale();
		this.createdAt = tenant.createdAt();
		this.updatedAt = tenant.updatedAt();
		this.closedAt = tenant.closedAt();
	}

	Tenant toDomain() {
		return Tenant.rehydrate(
			new TenantId(id),
			slug,
			name,
			status,
			timezone,
			locale,
			createdAt,
			updatedAt,
			closedAt
		);
	}
}
