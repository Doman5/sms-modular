package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.common.domain.ConflictException;
import com.domanski.smsmodular.integrationruntime.api.contract.OutboxEvent;
import com.domanski.smsmodular.integrationruntime.api.contract.OutboxPort;
import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.OutboxRepository;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;


@Service
public class OutboxPublisher implements OutboxPort {

	private final OutboxRepository store;
	private final Clock clock;

	public OutboxPublisher(OutboxRepository store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	@Override
	@TenantScopedTransactional
	public void publish(OutboxEvent event) {
		if (event == null) {
			throw new IllegalArgumentException("Outbox event is required");
		}
		TenantId currentTenant = TenantContext.require();
		if (!currentTenant.equals(event.tenantId())) {
			throw new IllegalArgumentException("Outbox tenant does not match verified tenant context");
		}
		Instant now = clock.instant();
		String correlationId = event.correlationId() == null
			? CorrelationContext.currentOrGenerate()
			: event.correlationId();
		try {
			store.save(new OutboxMessage(
				UUID.randomUUID(),
				event.tenantId(),
				event.topic(),
				event.aggregateType(),
				event.aggregateId(),
				event.payloadVersion(),
				event.payload(),
				OutboxStatus.PENDING,
				0,
				now,
				null,
				null,
				correlationId,
				event.idempotencyKey(),
				now,
				now,
				null
			));
		} catch (DataIntegrityViolationException exception) {
			throw new ConflictException("An event with this idempotency key already exists.");
		}
	}
}
