package com.domanski.smsmodular.audit.application;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.UUID;


public record AuditFilter(
	TenantId tenantId,
	Instant occurredFrom,
	Instant occurredTo,
	String actorType,
	UUID actorId,
	String module,
	String action,
	String subjectType,
	UUID subjectId
) {

	public AuditFilter {
		if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
			throw new IllegalArgumentException("Audit start time cannot be after end time");
		}
	}
}
