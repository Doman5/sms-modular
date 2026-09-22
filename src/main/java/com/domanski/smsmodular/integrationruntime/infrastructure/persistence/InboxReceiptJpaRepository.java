package com.domanski.smsmodular.integrationruntime.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InboxReceiptJpaRepository extends JpaRepository<InboxReceiptEntity, UUID> {

	boolean existsByTenantIdAndConsumerAndEventId(UUID tenantId, String consumer, UUID eventId);
}
