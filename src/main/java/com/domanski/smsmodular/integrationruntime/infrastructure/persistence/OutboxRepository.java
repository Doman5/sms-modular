package com.domanski.smsmodular.integrationruntime.infrastructure.persistence;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import com.domanski.smsmodular.integrationruntime.infrastructure.metrics.IntegrationRuntimeMetrics;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.ObjectProvider;


@Repository
@Profile("!test")
public class OutboxRepository {

	private final OutboxJpaRepository repository;
	private final JdbcTemplate jdbcTemplate;
	private final IntegrationRuntimeMetrics metrics;

	@Autowired
	public OutboxRepository(
		OutboxJpaRepository repository,
		JdbcTemplate jdbcTemplate,
		ObjectProvider<IntegrationRuntimeMetrics> metricsProvider
	) {
		this.repository = repository;
		this.jdbcTemplate = jdbcTemplate;
		this.metrics = metricsProvider.getIfAvailable();
	}

	
	public OutboxRepository() {
		this.repository = null;
		this.jdbcTemplate = null;
		this.metrics = null;
	}

	public OutboxMessage save(OutboxMessage message) {
		return repository.save(OutboxEntity.from(message)).toDomain();
	}

	public Optional<OutboxMessage> findById(UUID id) {
		return repository.findById(id).map(OutboxEntity::toDomain);
	}

	public Page<OutboxMessage> findDeadLetters(TenantId tenantId, Pageable pageable) {
		return repository.findByTenantIdAndStatus(tenantId.value(), OutboxStatus.DEAD_LETTER, pageable)
			.map(OutboxEntity::toDomain);
	}

	public List<OutboxMessage> leaseBatch(
		TenantId tenantId,
		String leaseOwner,
		Instant now,
		Instant leaseUntil,
		int batchSize
	) {
		if (metrics != null) {
			Integer recovered = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM outbox_messages WHERE tenant_id=? AND status='PROCESSING' AND lease_until <= ?",
				Integer.class, tenantId.value(), now
			);
			if (recovered != null && recovered > 0) {
				for (int i = 0; i < recovered; i++) {
					metrics.recoveredLease();
				}
			}
		}
		String sql = """
			WITH candidates AS (
				SELECT id
				FROM outbox_messages
				WHERE tenant_id = ?
				  AND ((status IN ('PENDING', 'RETRY_WAIT') AND available_at <= ?)
				    OR (status = 'PROCESSING' AND lease_until <= ?))
				ORDER BY available_at ASC, id ASC
				FOR UPDATE SKIP LOCKED
				LIMIT ?
			)
			UPDATE outbox_messages message
			SET status = 'PROCESSING',
				attempt = message.attempt + 1,
				lease_owner = ?,
				lease_until = ?,
				updated_at = ?
			FROM candidates
			WHERE message.id = candidates.id
			RETURNING message.id, message.tenant_id, message.topic, message.aggregate_type,
				message.aggregate_id, message.payload_version, message.payload, message.status,
				message.attempt, message.available_at, message.lease_owner, message.lease_until,
				message.correlation_id, message.idempotency_key, message.created_at,
				message.updated_at, message.last_error_code
			""";
		return jdbcTemplate.query(
			sql,
			this::map,
			tenantId.value(), now, now, batchSize, leaseOwner, leaseUntil, now
		);
	}

	public boolean markCompleted(TenantId tenantId, UUID id, String leaseOwner, Instant now) {
		return jdbcTemplate.update(
			"UPDATE outbox_messages SET status='COMPLETED', lease_owner=NULL, lease_until=NULL, updated_at=?, last_error_code=NULL "
				+ "WHERE id=? AND tenant_id=? AND status='PROCESSING' AND lease_owner=?",
			now, id, tenantId.value(), leaseOwner
		) == 1;
	}

	public boolean renewLease(TenantId tenantId, UUID id, String leaseOwner, Instant now, Instant leaseUntil) {
		return jdbcTemplate.update(
			"UPDATE outbox_messages SET lease_until=?, updated_at=? WHERE id=? AND tenant_id=? AND status='PROCESSING' AND lease_owner=?",
			leaseUntil, now, id, tenantId.value(), leaseOwner
		) == 1;
	}

	public boolean markRetry(
		TenantId tenantId,
		UUID id,
		String leaseOwner,
		Instant now,
		Instant availableAt,
		String errorCode
	) {
		return jdbcTemplate.update(
			"UPDATE outbox_messages SET status='RETRY_WAIT', available_at=?, lease_owner=NULL, lease_until=NULL, updated_at=?, last_error_code=? "
				+ "WHERE id=? AND tenant_id=? AND status='PROCESSING' AND lease_owner=?",
			availableAt, now, errorCode, id, tenantId.value(), leaseOwner
		) == 1;
	}

	public boolean markDeadLetter(TenantId tenantId, UUID id, String leaseOwner, Instant now, String errorCode) {
		return jdbcTemplate.update(
			"UPDATE outbox_messages SET status='DEAD_LETTER', lease_owner=NULL, lease_until=NULL, updated_at=?, last_error_code=? "
				+ "WHERE id=? AND tenant_id=? AND status='PROCESSING' AND lease_owner=?",
			now, errorCode, id, tenantId.value(), leaseOwner
		) == 1;
	}

	public boolean manualRetry(TenantId tenantId, UUID id, Instant now) {
		return jdbcTemplate.update(
			"UPDATE outbox_messages SET status='PENDING', attempt=0, available_at=?, lease_owner=NULL, lease_until=NULL, updated_at=?, last_error_code=NULL "
				+ "WHERE id=? AND tenant_id=? AND status='DEAD_LETTER'",
			now, now, id, tenantId.value()
		) == 1;
	}

	private OutboxMessage map(ResultSet result, int row) throws SQLException {
		return new OutboxMessage(
			result.getObject("id", UUID.class),
			TenantId.of(result.getObject("tenant_id", UUID.class)),
			result.getString("topic"),
			result.getString("aggregate_type"),
			result.getObject("aggregate_id", UUID.class),
			result.getInt("payload_version"),
			result.getString("payload"),
			OutboxStatus.valueOf(result.getString("status")),
			result.getInt("attempt"),
			result.getTimestamp("available_at").toInstant(),
			result.getString("lease_owner"),
			result.getTimestamp("lease_until") == null ? null : result.getTimestamp("lease_until").toInstant(),
			result.getString("correlation_id"),
			result.getString("idempotency_key"),
			result.getTimestamp("created_at").toInstant(),
			result.getTimestamp("updated_at").toInstant(),
			result.getString("last_error_code")
		);
	}
}
