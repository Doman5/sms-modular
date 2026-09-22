package com.domanski.smsmodular.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.audit.application.AuditFilter;
import com.domanski.smsmodular.audit.application.AuditQueryService;
import com.domanski.smsmodular.support.PostgresIntegrationTest;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleEventPublisher;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleService;
import com.domanski.smsmodular.tenancy.application.TenantSettingsCommand;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
@Import(AuditTransactionRollbackPostgresTest.RollbackConfiguration.class)
class AuditTransactionRollbackPostgresTest extends PostgresIntegrationTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);

	@org.springframework.beans.factory.annotation.Autowired
	private TenantLifecycleService tenantLifecycleService;

	@org.springframework.beans.factory.annotation.Autowired
	private TenantRepository tenantStore;

	@org.springframework.beans.factory.annotation.Autowired
	private AuditQueryService auditQueryService;


	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.liquibase.user", POSTGRES::getUsername);
		registry.add("spring.liquibase.password", POSTGRES::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
	}

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void tenantAndAuditWritesRollbackTogetherWhenTheCriticalCommandFails() {
		Tenant tenant = Tenant.create(
			"rollback-" + UUID.randomUUID().toString().substring(0, 8),
			"Before",
			"UTC",
			"en",
			CLOCK
		);
		tenantStore.save(tenant);

		assertThatThrownBy(() -> tenantLifecycleService.update(
			tenant.id(), new TenantSettingsCommand("After", null, null)
		)).isInstanceOf(IllegalStateException.class).hasMessage("simulated downstream failure");

		assertThat(tenantStore.findById(tenant.id().value()).orElseThrow().name()).isEqualTo("Before");
		long auditRows = TenantContext.callWith(tenant.id(), () -> auditQueryService
			.findForCurrentTenant(new AuditFilter(null, null, null, null, null, "TENANCY", "SETTINGS_UPDATED", null, null),
				org.springframework.data.domain.PageRequest.of(0, 25))
			.getTotalElements());
		assertThat(auditRows).isZero();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class RollbackConfiguration {

		@Bean
		@Primary
		TenantLifecycleEventPublisher failingPublisher() {
			return event -> {
				throw new IllegalStateException("simulated downstream failure");
			};
		}
	}
}
