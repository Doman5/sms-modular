package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.OutboxRepository;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;


@Service
public class OutboxLeaseService {

	private final OutboxRepository store;

	public OutboxLeaseService(OutboxRepository store) {
		this.store = store;
	}

	@TenantScopedTransactional
	public List<OutboxMessage> lease(TenantId tenantId, String owner, Instant now, Instant leaseUntil, int batchSize) {
		verifyTenant(tenantId);
		return store.leaseBatch(tenantId, owner, now, leaseUntil, batchSize);
	}

	@TenantScopedTransactional
	public boolean complete(TenantId tenantId, UUID id, String owner, Instant now) {
		verifyTenant(tenantId);
		return store.markCompleted(tenantId, id, owner, now);
	}

	@TenantScopedTransactional
	public boolean renew(TenantId tenantId, UUID id, String owner, Instant now, Instant leaseUntil) {
		verifyTenant(tenantId);
		return store.renewLease(tenantId, id, owner, now, leaseUntil);
	}

	@TenantScopedTransactional
	public boolean retry(
		TenantId tenantId,
		UUID id,
		String owner,
		Instant now,
		Instant availableAt,
		String errorCode
	) {
		verifyTenant(tenantId);
		return store.markRetry(tenantId, id, owner, now, availableAt, errorCode);
	}

	@TenantScopedTransactional
	public boolean deadLetter(TenantId tenantId, UUID id, String owner, Instant now, String errorCode) {
		verifyTenant(tenantId);
		return store.markDeadLetter(tenantId, id, owner, now, errorCode);
	}

	private void verifyTenant(TenantId tenantId) {
		if (tenantId == null || !tenantId.equals(TenantContext.require())) {
			throw new IllegalArgumentException("Tenant context does not match the job tenant");
		}
	}
}
