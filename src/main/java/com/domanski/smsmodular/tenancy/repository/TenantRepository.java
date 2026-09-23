package com.domanski.smsmodular.tenancy.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.domanski.smsmodular.tenancy.entity.Tenant;
import com.domanski.smsmodular.tenancy.api.TenantStatus;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

	Optional<Tenant> findBySlug(String slug);

	boolean existsBySlug(String slug);

	Page<Tenant> findAllByStatus(TenantStatus status, Pageable pageable);

	@Query("select tenant from Tenant tenant where (:status is null or tenant.status = :status) and "
			+ "(:search = '' or lower(tenant.name) like concat('%', :search, '%') "
			+ "or lower(tenant.slug) like concat('%', :search, '%'))")
	Page<Tenant> search(@Param("status") TenantStatus status, @Param("search") String search, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select tenant from Tenant tenant where tenant.id = :id")
	Optional<Tenant> findLockedById(@Param("id") UUID id);
}
