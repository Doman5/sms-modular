package com.domanski.smsmodular.integrationruntime.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.domanski.smsmodular.integrationruntime.entity.OutboxMessage;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
	@Query(value = "SELECT * FROM outbox_messages WHERE "
			+ "((status IN ('PENDING','RETRY_WAIT') AND available_at <= :now) "
			+ "OR (status = 'PROCESSING' AND lease_until < :now)) "
			+ "ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 20", nativeQuery = true)
	List<OutboxMessage> claimable(@Param("now") Instant now);

	Optional<OutboxMessage> findByTenantIdAndId(UUID tenantId, UUID id);

	List<OutboxMessage> findByTenantIdAndSmsMessageId(UUID tenantId, UUID smsMessageId);
}
