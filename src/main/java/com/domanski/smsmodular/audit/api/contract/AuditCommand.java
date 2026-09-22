package com.domanski.smsmodular.audit.api.contract;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;







public record AuditCommand(
	TenantId tenantId,
	ActorRef actor,
	String module,
	String action,
	String subjectType,
	UUID subjectId,
	String outcome,
	Instant occurredAt,
	String correlationId,
	Map<String, String> metadata,
	boolean platformOperation
) {

	public AuditCommand {
		if (tenantId == null && !platformOperation) {
			throw new IllegalArgumentException("A tenant is required for a tenant audit operation");
		}
		if (tenantId != null && platformOperation) {
			throw new IllegalArgumentException("A platform audit operation cannot carry a tenant ID");
		}
		if (module == null || module.isBlank()) {
			throw new IllegalArgumentException("Audit module is required");
		}
		if (action == null || action.isBlank()) {
			throw new IllegalArgumentException("Audit action is required");
		}
		if (subjectType == null || subjectType.isBlank()) {
			throw new IllegalArgumentException("Audit subject type is required");
		}
		if (outcome == null || outcome.isBlank()) {
			throw new IllegalArgumentException("Audit outcome is required");
		}
		if (platformOperation && actor != null && !actor.isPlatformActor()) {
			throw new IllegalArgumentException("A platform operation requires a platform actor");
		}
		metadata = metadata == null
			? Map.of()
			: Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
	}

	public static AuditCommand tenant(
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
		return new AuditCommand(
			Objects.requireNonNull(tenantId, "Tenant ID is required"), actor, module, action,
			subjectType, subjectId, outcome, occurredAt, correlationId, metadata, false
		);
	}

	public static AuditCommand platform(
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
		return new AuditCommand(
			null, actor, module, action, subjectType, subjectId, outcome, occurredAt,
			correlationId, metadata, true
		);
	}
}
