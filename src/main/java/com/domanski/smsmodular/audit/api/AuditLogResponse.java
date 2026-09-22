package com.domanski.smsmodular.audit.api;

import com.domanski.smsmodular.audit.application.AuditEntryView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;


public record AuditLogResponse(
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

	public static AuditLogResponse from(AuditEntryView view) {
		return new AuditLogResponse(
			view.id(), view.tenantId(), view.actorType(), view.actorId(), view.module(), view.action(),
			view.subjectType(), view.subjectId(), view.outcome(), view.occurredAt(), view.correlationId(),
			view.metadata()
		);
	}
}
