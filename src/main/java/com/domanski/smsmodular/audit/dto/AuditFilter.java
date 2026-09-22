package com.domanski.smsmodular.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.domanski.smsmodular.audit.api.AuditResult;

public record AuditFilter(Instant from, Instant to, UUID actorId, String module,
		String action, AuditResult result, UUID targetId) {
}
