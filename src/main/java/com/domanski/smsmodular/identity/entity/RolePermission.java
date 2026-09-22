package com.domanski.smsmodular.identity.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "role_permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "role_id", nullable = false)
	private UUID roleId;

	@Column(name = "permission_code", nullable = false, length = 80)
	private String permissionCode;

	public RolePermission(UUID id, UUID tenantId, UUID roleId, String permissionCode) {
		this.id = id;
		this.tenantId = tenantId;
		this.roleId = roleId;
		this.permissionCode = permissionCode;
	}

}
