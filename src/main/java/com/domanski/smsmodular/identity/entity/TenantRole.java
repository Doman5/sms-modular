package com.domanski.smsmodular.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant_roles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantRole {

	@Id
	@Setter(AccessLevel.NONE)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Setter(AccessLevel.NONE)
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Setter(AccessLevel.NONE)
	@Column(nullable = false, length = 64)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public TenantRole(UUID id, UUID tenantId, String code, String name, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.code = code;
		this.name = name;
		this.createdAt = now;
		this.updatedAt = now;
	}

}
