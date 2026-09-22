package com.domanski.smsmodular.tenancy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

	Optional<TenantEntity> findBySlug(String slug);
}
