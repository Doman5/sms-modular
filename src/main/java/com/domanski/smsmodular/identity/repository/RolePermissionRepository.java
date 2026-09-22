package com.domanski.smsmodular.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.domanski.smsmodular.identity.entity.RolePermission;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

	List<RolePermission> findByTenantIdAndRoleId(UUID tenantId, UUID roleId);

	void deleteByTenantIdAndRoleId(UUID tenantId, UUID roleId);
}
