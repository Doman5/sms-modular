package com.domanski.smsmodular.tenancy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.common.api.ApiProblemCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TenantDomainTest {

	private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
	private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

	@Test
	void createsNormalizedTenantWithIanaTimezoneAndBcp47Locale() {
		Tenant tenant = Tenant.create("Acme-PL", "  Acme Sp. z o.o. ", "Europe/Warsaw", "pl-pl", CLOCK);

		assertThat(tenant.slug()).isEqualTo("acme-pl");
		assertThat(tenant.name()).isEqualTo("Acme Sp. z o.o.");
		assertThat(tenant.timezone()).isEqualTo("Europe/Warsaw");
		assertThat(tenant.locale()).isEqualTo("pl-PL");
		assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
		assertThat(tenant.closedAt()).isNull();
	}

	@Test
	void supportsActiveSuspendedActiveAndIrreversibleClose() {
		Tenant tenant = Tenant.create("acme", "Acme", "UTC", "en", CLOCK);

		tenant.suspend(CLOCK);
		assertThat(tenant.status()).isEqualTo(TenantStatus.SUSPENDED);
		tenant.activate(CLOCK);
		assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
		tenant.close(CLOCK);

		assertThat(tenant.status()).isEqualTo(TenantStatus.CLOSED);
		assertThat(tenant.closedAt()).isEqualTo(CREATED_AT);
		assertThatThrownBy(() -> tenant.activate(CLOCK))
			.isInstanceOf(TenantStatusTransitionException.class)
			.extracting("problemCode")
			.isEqualTo(ApiProblemCode.TENANT_STATUS_TRANSITION_INVALID);
		assertThatThrownBy(() -> tenant.updateSettings("New", "UTC", "en", CLOCK))
			.isInstanceOf(TenantStatusTransitionException.class)
			.extracting("problemCode")
			.isEqualTo(ApiProblemCode.TENANT_CLOSED);
	}

	@Test
	void rejectsInvalidTransitionsAndSettings() {
		Tenant tenant = Tenant.create("acme", "Acme", "Europe/Warsaw", "pl-PL", CLOCK);

		assertThatThrownBy(() -> tenant.activate(CLOCK))
			.isInstanceOf(TenantStatusTransitionException.class);
		tenant.suspend(CLOCK);
		assertThatThrownBy(() -> tenant.suspend(CLOCK))
			.isInstanceOf(TenantStatusTransitionException.class);
		assertThatThrownBy(() -> Tenant.create("bad slug", "Acme", "Europe/Warsaw", "pl-PL", CLOCK))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> Tenant.create("acme", "Acme", "+02:00", "pl-PL", CLOCK))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> Tenant.create("acme", "Acme", "Europe/Warsaw", "pl_PL", CLOCK))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	void suspendedTenantCannotRunBusinessCommands() {
		Tenant tenant = Tenant.create("acme", "Acme", "UTC", "en", CLOCK);
		tenant.suspend(CLOCK);

		assertThatThrownBy(tenant::requireActiveForCommand)
			.isInstanceOf(TenantStatusTransitionException.class)
			.extracting("problemCode")
			.isEqualTo(ApiProblemCode.TENANT_SUSPENDED);
	}
}
