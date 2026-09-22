package com.domanski.smsmodular.tenancy.api.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

	private final TenantId tenantA = TenantId.of(UUID.randomUUID());
	private final TenantId tenantB = TenantId.of(UUID.randomUUID());

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	@Test
	void runWithRestoresNestedContextAndClearsAfterWorkerStyleFailure() {
		assertThatThrownBy(TenantContext::require)
			.isInstanceOf(TenantContextRequiredException.class);

		assertThatThrownBy(() -> TenantContext.runWith(tenantA, () -> {
			assertThat(TenantContext.require()).isEqualTo(tenantA);
			TenantContext.runWith(tenantB, () -> assertThat(TenantContext.require()).isEqualTo(tenantB));
			assertThat(TenantContext.require()).isEqualTo(tenantA);
			throw new IllegalStateException("worker failed");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(TenantContext.current()).isEmpty();
	}

	@Test
	void outOfOrderCloseFailsClosedInsteadOfLeakingTheInnerValue() {
		TenantContext.Scope outer = TenantContext.open(tenantA);
		TenantContext.Scope inner = TenantContext.open(tenantB);

		assertThatThrownBy(outer::close)
			.isInstanceOf(IllegalStateException.class);
		assertThat(TenantContext.current()).isEmpty();
		assertThatThrownBy(inner::close)
			.isInstanceOf(IllegalStateException.class);
	}
}
