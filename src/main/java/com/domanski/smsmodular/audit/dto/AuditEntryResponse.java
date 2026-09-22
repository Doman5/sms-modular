package com.domanski.smsmodular.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.domanski.smsmodular.audit.api.AuditActorType;
import com.domanski.smsmodular.audit.api.AuditResult;
import com.domanski.smsmodular.audit.entity.AuditEntry;

public record AuditEntryResponse(UUID id, UUID tenantId, AuditActorType actorType, UUID actorId,
		String module, String action, String targetType, UUID targetId, AuditResult result,
		Instant occurredAt, String correlationId, Map<String, Object> metadata) {

	public static AuditEntryResponse from(AuditEntry entry) {
		return new AuditEntryResponse(entry.getId(), entry.getTenantId(), entry.getActorType(),
				entry.getActorId(), entry.getModule(), entry.getAction(), entry.getTargetType(),
				entry.getTargetId(), entry.getResult(), entry.getOccurredAt(),
				entry.getCorrelationId(), entry.getMetadata());
	}
}
