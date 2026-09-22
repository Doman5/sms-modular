package com.domanski.smsmodular.audit.domain;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public record AuditEntry(
	UUID id,
	TenantId tenantId,
	ActorRef actor,
	String module,
	String action,
	String subjectType,
	UUID subjectId,
	String outcome,
	Instant occurredAt,
	String correlationId,
	Map<String, String> metadata
) {

	public AuditEntry {
		Objects.requireNonNull(id, "Audit ID is required");
		Objects.requireNonNull(actor, "Audit actor is required");
		module = code(module, "Audit module");
		action = code(action, "Audit action");
		subjectType = code(subjectType, "Audit subject type");
		outcome = code(outcome, "Audit outcome");
		if (tenantId == null && !actor.isPlatformActor()) {
			throw new IllegalArgumentException("A platform audit entry requires a platform actor");
		}
		Objects.requireNonNull(occurredAt, "Audit timestamp is required");
		if (correlationId == null || !CorrelationContext.isSafe(correlationId)) {
			throw new IllegalArgumentException("Audit correlation ID has an invalid format");
		}
		metadata = AuditMetadataPolicy.sanitize(metadata);
	}

	private static String code(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z][A-Z0-9_.-]{0,95}")) {
			throw new IllegalArgumentException(label + " has an invalid format");
		}
		return normalized;
	}
}
