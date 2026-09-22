package com.domanski.smsmodular.audit.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AuditJpaRepository extends JpaRepository<AuditEntity, UUID>, JpaSpecificationExecutor<AuditEntity> {
}
