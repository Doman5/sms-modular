package com.domanski.smsmodular.integrationruntime.infrastructure.persistence;

import com.domanski.smsmodular.integrationruntime.domain.InboxReceipt;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


@Repository
@Profile("!test")
public class InboxReceiptRepository {

	private final JdbcTemplate jdbcTemplate;

	@Autowired
	public InboxReceiptRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	
	public InboxReceiptRepository() {
		this.jdbcTemplate = null;
	}

	public boolean recordIfNew(InboxReceipt receipt) {
		try {
			return jdbcTemplate.update(
				"INSERT INTO inbox_receipts (id, tenant_id, consumer, event_id, received_at, correlation_id) "
					+ "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, consumer, event_id) DO NOTHING",
				receipt.id(), receipt.tenantId().value(), receipt.consumer(), receipt.eventId(),
				receipt.receivedAt(), receipt.correlationId()
			) == 1;
		} catch (DataIntegrityViolationException exception) {
			
			return false;
		}
	}

	public boolean exists(TenantId tenantId, String consumer, UUID eventId) {
		Boolean value = jdbcTemplate.queryForObject(
			"SELECT EXISTS (SELECT 1 FROM inbox_receipts WHERE tenant_id=? AND consumer=? AND event_id=?)",
			Boolean.class, tenantId.value(), consumer, eventId
		);
		return Boolean.TRUE.equals(value);
	}
}
