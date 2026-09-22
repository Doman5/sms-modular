package com.domanski.smsmodular.integrationruntime.api.contract;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.Objects;
import java.util.UUID;





public record SmsDispatchCommand(
	TenantId tenantId,
	UUID dispatchId,
	String recipient,
	String message,
	String idempotencyKey
) {

	public SmsDispatchCommand {
		Objects.requireNonNull(tenantId, "Dispatch tenant is required");
		Objects.requireNonNull(dispatchId, "Dispatch ID is required");
		if (recipient == null || recipient.isBlank() || recipient.length() > 64) {
			throw new IllegalArgumentException("Dispatch recipient is invalid");
		}
		if (message == null || message.isBlank() || message.length() > 1_600) {
			throw new IllegalArgumentException("Dispatch message is invalid");
		}
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
			throw new IllegalArgumentException("Dispatch idempotency key is required");
		}
	}
}
