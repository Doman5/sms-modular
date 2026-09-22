package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.integrationruntime.domain.InboxReceipt;
import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.InboxReceiptRepository;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;






@Service
public class HandlerExecutionService {

	private final InboxReceiptRepository receipts;
	private final Clock clock;

	public HandlerExecutionService(InboxReceiptRepository receipts, Clock clock) {
		this.receipts = receipts;
		this.clock = clock;
	}

	@TenantScopedTransactional
	public boolean execute(OutboxMessage message, OutboxHandler handler, JobContext context) throws Exception {
		if (!TenantContext.require().equals(message.tenantId())) {
			throw new IllegalArgumentException("Handler tenant does not match job tenant");
		}
		boolean firstDelivery = receipts.recordIfNew(new InboxReceipt(
			UUID.randomUUID(),
			message.tenantId(),
			handler.consumer(),
			message.id(),
			clock.instant(),
			CorrelationContext.requireCurrent()
		));
		if (!firstDelivery) {
			return false;
		}
		handler.handle(message, context);
		return true;
	}
}
