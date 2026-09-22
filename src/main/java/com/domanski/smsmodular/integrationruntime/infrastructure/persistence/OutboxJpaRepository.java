package com.domanski.smsmodular.integrationruntime.infrastructure.persistence;

import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {

	Page<OutboxEntity> findByStatus(OutboxStatus status, Pageable pageable);

	Page<OutboxEntity> findByTenantIdAndStatus(UUID tenantId, OutboxStatus status, Pageable pageable);
}
