package com.domanski.smsmodular.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.domanski.smsmodular.identity.entity.TenantRole;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {

	Optional<TenantRole> findByTenantIdAndId(UUID tenantId, UUID id);

	Optional<TenantRole> findByTenantIdAndCode(UUID tenantId, String code);

	List<TenantRole> findByTenantIdOrderByNameAsc(UUID tenantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select role from TenantRole role where role.tenantId = :tenantId and role.id = :id")
	Optional<TenantRole> findLockedByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
