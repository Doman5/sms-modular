package com.domanski.smsmodular.audit.application;

import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import com.domanski.smsmodular.audit.infrastructure.persistence.AuditRepository;


@Service
public class AuditQueryService {

	private final AuditRepository repository;
	private final JdbcTemplate jdbcTemplate;

	public AuditQueryService(AuditRepository repository, ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
		this.repository = repository;
		this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
	}

	@TenantScopedTransactional(readOnly = true)
	public Page<AuditEntryView> findForCurrentTenant(AuditFilter filter, Pageable pageable) {
		TenantId tenantId = TenantContext.require();
		AuditFilter scoped = new AuditFilter(
			tenantId,
			filter.occurredFrom(),
			filter.occurredTo(),
			filter.actorType(),
			filter.actorId(),
			filter.module(),
			filter.action(),
			filter.subjectType(),
			filter.subjectId()
		);
		return repository.find(scoped, pageable).map(AuditEntryView::from);
	}

	@Transactional(readOnly = true)
	public Page<AuditEntryView> findForPlatform(TenantId tenantId, AuditFilter filter, Pageable pageable) {
		if (tenantId == null) {
			throw new IllegalArgumentException("A tenant filter is required for platform audit queries");
		}
		setPlatformTenantContext(tenantId);
		AuditFilter scoped = new AuditFilter(
			tenantId,
			filter.occurredFrom(),
			filter.occurredTo(),
			filter.actorType(),
			filter.actorId(),
			filter.module(),
			filter.action(),
			filter.subjectType(),
			filter.subjectId()
		);
		return repository.find(scoped, pageable).map(AuditEntryView::from);
	}

	@TenantScopedTransactional(readOnly = true)
	public AuditEntryView getForCurrentTenant(UUID auditId) {
		TenantContext.require();
		return repository.findById(auditId).map(AuditEntryView::from)
			.orElseThrow(() -> new EntityNotFoundException("Audit entry not found"));
	}

	@Transactional(readOnly = true)
	public AuditEntryView getForPlatform(TenantId tenantId, UUID auditId) {
		if (tenantId == null) {
			throw new IllegalArgumentException("A tenant filter is required for platform audit queries");
		}
		setPlatformTenantContext(tenantId);
		return repository.findById(auditId)
			.filter(entry -> tenantId.equals(entry.tenantId()))
			.map(AuditEntryView::from)
			.orElseThrow(() -> new EntityNotFoundException("Audit entry not found"));
	}

	private void setPlatformTenantContext(TenantId tenantId) {
		if (jdbcTemplate == null) {
			return;
		}
		jdbcTemplate.queryForObject(
			"select set_config('app.tenant_id', ?, true)", String.class, tenantId.value().toString()
		);
		jdbcTemplate.queryForObject("select set_config('app.audit_platform', 'false', true)", String.class);
	}
}
