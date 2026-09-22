package com.domanski.smsmodular.audit.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import com.domanski.smsmodular.audit.entity.AuditEntry;

public interface AuditRepository extends Repository<AuditEntry, UUID>, JpaSpecificationExecutor<AuditEntry> {

	AuditEntry save(AuditEntry entry);
}
