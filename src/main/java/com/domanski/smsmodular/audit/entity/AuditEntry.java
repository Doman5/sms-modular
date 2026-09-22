package com.domanski.smsmodular.audit.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.domanski.smsmodular.audit.api.AuditActorType;
import com.domanski.smsmodular.audit.api.AuditResult;

@Entity
@Table(name = "audit_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEntry {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "tenant_id", updatable = false)
	private UUID tenantId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, updatable = false, length = 24)
	private AuditActorType actorType;

	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Column(name = "module_code", nullable = false, updatable = false, length = 64)
	private String module;

	@Column(name = "action_code", nullable = false, updatable = false, length = 80)
	private String action;

	@Column(name = "target_type", nullable = false, updatable = false, length = 64)
	private String targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private UUID targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 16)
	private AuditResult result;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "correlation_id", nullable = false, updatable = false, length = 100)
	private String correlationId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> metadata;

	public AuditEntry(UUID id, UUID tenantId, AuditActorType actorType, UUID actorId, String module,
			String action, String targetType, UUID targetId, AuditResult result, Instant occurredAt,
			String correlationId, Map<String, Object> metadata) {
		this.id = id;
		this.tenantId = tenantId;
		this.actorType = actorType;
		this.actorId = actorId;
		this.module = module;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.result = result;
		this.occurredAt = occurredAt;
		this.correlationId = correlationId;
		this.metadata = metadata;
	}
}
