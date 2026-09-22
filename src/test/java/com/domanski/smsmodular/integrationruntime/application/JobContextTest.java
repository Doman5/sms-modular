package com.domanski.smsmodular.integrationruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class JobContextTest {

	private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

	@AfterEach
	void clearThreadState() {
		TenantContext.clear();
		CorrelationContext.clear();
		MDC.clear();
	}

	@Test
	void propagatesTenantCorrelationAndMdcForOneTaskThenClearsEverything() throws Exception {
		TenantContext.open(TenantId.of(UUID.randomUUID()));
		CorrelationContext.set("stale-correlation");
		MDC.put("tenantId", "stale-tenant");

		JobContext.run(message(), context -> {
			assertThat(context.tenantId()).isEqualTo(TENANT);
			assertThat(context.correlationId()).isEqualTo("job-correlation");
			assertThat(TenantContext.require()).isEqualTo(TENANT);
			assertThat(CorrelationContext.requireCurrent()).isEqualTo("job-correlation");
			assertThat(MDC.get("tenantId")).isEqualTo(TENANT.value().toString());
			assertThat(MDC.get(CorrelationContext.MDC_KEY)).isEqualTo("job-correlation");
		});

		assertThat(TenantContext.current()).isEmpty();
		assertThat(CorrelationContext.current()).isNull();
		assertThat(MDC.get("tenantId")).isNull();
		assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
	}

	@Test
	void clearsAllContextsWhenTheHandlerFails() {
		assertThatThrownBy(() -> JobContext.run(message(), context -> {
			assertThat(TenantContext.require()).isEqualTo(TENANT);
			throw new IllegalStateException("handler failure");
		})).isInstanceOf(IllegalStateException.class).hasMessage("handler failure");

		assertThat(TenantContext.current()).isEmpty();
		assertThat(CorrelationContext.current()).isNull();
		assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
	}

	private OutboxMessage message() {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		return new OutboxMessage(
			UUID.randomUUID(), TENANT, "TENANT.CREATED", "TENANT", UUID.randomUUID(), 1,
			"{}", OutboxStatus.PENDING, 0, now, null, null, "job-correlation", "job-id", now, now, null
		);
	}
}
