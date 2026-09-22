package com.domanski.smsmodular.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

	@Test
	void normalizesStableCodesAndKeepsTenantAndCorrelation() {
		UUID tenant = UUID.randomUUID();
		AuditEntry entry = new AuditEntry(
			UUID.randomUUID(), TenantId.of(tenant), ActorRef.system(), "tenancy", "settings_updated",
			"tenant", tenant, "success", Instant.parse("2026-01-01T00:00:00Z"), "audit-test-1",
			Map.of("operation", "SETTINGS_UPDATED")
		);

		assertThat(entry.module()).isEqualTo("TENANCY");
		assertThat(entry.action()).isEqualTo("SETTINGS_UPDATED");
		assertThat(entry.outcome()).isEqualTo("SUCCESS");
	}

	@Test
	void platformRowsRequireAPlatformActorAndSafeCorrelation() {
		assertThatThrownBy(() -> new AuditEntry(
			UUID.randomUUID(), null, ActorRef.system(), "TENANCY", "CREATED", "TENANT", null,
			"SUCCESS", Instant.now(), "audit-test-2", Map.of()
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AuditEntry(
			UUID.randomUUID(), TenantId.of(UUID.randomUUID()), ActorRef.system(), "TENANCY", "CREATED",
			"TENANT", null, "SUCCESS", Instant.now(), "unsafe value", Map.of()
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
