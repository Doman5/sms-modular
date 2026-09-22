package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.audit.api.contract.AuditCommand;
import com.domanski.smsmodular.audit.api.contract.AuditPort;
import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.common.domain.ConflictException;
import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.OutboxRepository;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class OutboxAdminService {

	private final OutboxRepository store;
	private final AuditPort auditPort;
	private final Clock clock;
	private final JdbcTemplate jdbcTemplate;

	public OutboxAdminService(
		OutboxRepository store,
		AuditPort auditPort,
		Clock clock,
		ObjectProvider<JdbcTemplate> jdbcTemplateProvider
	) {
		this.store = store;
		this.auditPort = auditPort;
		this.clock = clock;
		this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
	}

	@Transactional(readOnly = true)
	public Page<OutboxMessage> findDeadLetters(TenantId tenantId, Pageable pageable) {
		return inTenant(tenantId, () -> store.findDeadLetters(tenantId, pageable));
	}

	@Transactional(readOnly = true)
	public OutboxMessage findDeadLetter(TenantId tenantId, UUID id) {
		return inTenant(tenantId, () -> store.findById(id)
			.filter(message -> message.tenantId().equals(tenantId) && message.status() == com.domanski.smsmodular.integrationruntime.domain.OutboxStatus.DEAD_LETTER)
			.orElseThrow(() -> new EntityNotFoundException("Dead letter not found")));
	}

	@Transactional
	public OutboxMessage retryDeadLetter(TenantId tenantId, UUID id, ActorRef actor) {
		return inTenant(tenantId, () -> {
			OutboxMessage before = store.findById(id)
				.filter(message -> message.tenantId().equals(tenantId))
				.orElseThrow(() -> new EntityNotFoundException("Dead letter not found"));
			if (before.status() != com.domanski.smsmodular.integrationruntime.domain.OutboxStatus.DEAD_LETTER) {
				throw new ConflictException("Only a dead letter can be manually retried.");
			}
			if (!store.manualRetry(tenantId, id, clock.instant())) {
				throw new ConflictException("The dead letter was changed by another operation.");
			}
			
			
			auditPort.record(AuditCommand.tenant(
				tenantId,
				actor == null ? ActorRef.platform(null) : actor,
				"INTEGRATION_RUNTIME",
				"DEAD_LETTER_RETRIED",
				"OUTBOX_MESSAGE",
				id,
				"SUCCESS",
				clock.instant(),
				CorrelationContext.currentOrGenerate(),
				Map.of("operation", "MANUAL_RETRY")
			));
			return store.findById(id).orElseThrow(() -> new EntityNotFoundException("Dead letter not found"));
		});
	}

	private <T> T inTenant(TenantId tenantId, java.util.function.Supplier<T> operation) {
		if (tenantId == null) {
			throw new IllegalArgumentException("A tenant filter is required");
		}
		setTenantDatabaseContext(tenantId);
		try (TenantContext.Scope ignored = TenantContext.open(tenantId)) {
			return operation.get();
		} finally {
			TenantContext.clear();
		}
	}

	private void setTenantDatabaseContext(TenantId tenantId) {
		if (jdbcTemplate == null) {
			return;
		}
		jdbcTemplate.queryForObject(
			"select set_config('app.tenant_id', ?, true)", String.class, tenantId.value().toString()
		);
		jdbcTemplate.queryForObject("select set_config('app.audit_platform', 'false', true)", String.class);
	}
}
