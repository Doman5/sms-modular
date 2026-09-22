package com.domanski.smsmodular.audit.infrastructure.persistence;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.audit.domain.AuditEntry;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "audit_entries")
class AuditEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "tenant_id", updatable = false)
	private UUID tenantId;

	@Column(name = "actor_type", nullable = false, updatable = false, length = 32)
	private String actorType;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Column(name = "module", nullable = false, updatable = false, length = 96)
	private String module;

	@Column(name = "action", nullable = false, updatable = false, length = 96)
	private String action;

	@Column(name = "subject_type", nullable = false, updatable = false, length = 96)
	private String subjectType;

	@Column(name = "subject_id", updatable = false)
	private UUID subjectId;

	@Column(name = "outcome", nullable = false, updatable = false, length = 32)
	private String outcome;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "correlation_id", nullable = false, updatable = false, length = 64)
	private String correlationId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "metadata", nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, String> metadata;

	protected AuditEntity() {
	}

	static AuditEntity from(AuditEntry entry) {
		AuditEntity entity = new AuditEntity();
		entity.id = entry.id();
		entity.tenantId = entry.tenantId() == null ? null : entry.tenantId().value();
		entity.actorType = entry.actor().type();
		entity.actorId = entry.actor().id();
		entity.module = entry.module();
		entity.action = entry.action();
		entity.subjectType = entry.subjectType();
		entity.subjectId = entry.subjectId();
		entity.outcome = entry.outcome();
		entity.occurredAt = entry.occurredAt();
		entity.correlationId = entry.correlationId();
		entity.metadata = entry.metadata();
		return entity;
	}

	AuditEntry toDomain() {
		return new AuditEntry(
			id,
			tenantId == null ? null : TenantId.of(tenantId),
			new ActorRef(actorType, actorId),
			module,
			action,
			subjectType,
			subjectId,
			outcome,
			occurredAt,
			correlationId,
			metadata
		);
	}
}
