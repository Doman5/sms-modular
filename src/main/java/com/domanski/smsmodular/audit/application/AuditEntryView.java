package com.domanski.smsmodular.audit.application;

import com.domanski.smsmodular.audit.domain.AuditEntry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;


public record AuditEntryView(
	UUID id,
	UUID tenantId,
	String actorType,
	UUID actorId,
	String module,
	String action,
	String subjectType,
	UUID subjectId,
	String outcome,
	Instant occurredAt,
	String correlationId,
	Map<String, String> metadata
) {

	public static AuditEntryView from(AuditEntry entry) {
		return new AuditEntryView(
			entry.id(),
			entry.tenantId() == null ? null : entry.tenantId().value(),
			entry.actor().type(),
			entry.actor().id(),
			entry.module(),
			entry.action(),
			entry.subjectType(),
			entry.subjectId(),
			entry.outcome(),
			entry.occurredAt(),
			entry.correlationId(),
			entry.metadata()
		);
	}
}
