package com.domanski.smsmodular.integrationruntime.api.contract;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


public record SmsDispatchResult(
	TenantId tenantId,
	UUID dispatchId,
	SmsDispatchStatus status,
	String providerMessageId,
	Instant observedAt
) {

	public SmsDispatchResult {
		Objects.requireNonNull(tenantId, "Dispatch tenant is required");
		Objects.requireNonNull(dispatchId, "Dispatch ID is required");
		Objects.requireNonNull(status, "Dispatch status is required");
		Objects.requireNonNull(observedAt, "Dispatch observation time is required");
	}
}
