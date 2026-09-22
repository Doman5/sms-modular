package com.domanski.smsmodular.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.support.PostgresIntegrationTest;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantContextRequiredException;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleService;
import com.domanski.smsmodular.tenancy.infrastructure.transaction.TenantScopedTransactionAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
@Import(TenantScopedTransactionPostgresTest.ProbeConfiguration.class)
class TenantScopedTransactionPostgresTest extends PostgresIntegrationTest {

	@Autowired
	private TenantLifecycleService tenantLifecycleService;

	@Autowired
	private TransactionProbe transactionProbe;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ApplicationContext applicationContext;

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
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	void aspectSetsTenantInsideTransactionAndLocalSettingDisappearsAfterCommit() throws Exception {
		TenantId tenantId = tenantLifecycleService
			.create("tx-" + java.util.UUID.randomUUID().toString().substring(0, 8), "Transaction probe", "UTC", "en")
			.id();

		String observed = TenantContext.callWith(tenantId, transactionProbe::currentSetting);
		assertThat(observed).isEqualTo(tenantId.value().toString());
		String currentTenant = jdbcTemplate.queryForObject("SELECT current_setting('app.tenant_id', true)", String.class);
		assertThat(currentTenant == null || currentTenant.isEmpty()).isTrue();
		assertThat(applicationContext.getBeansOfType(TenantScopedTransactionAspect.class)).hasSize(1);
		assertThat(AopUtils.isAopProxy(transactionProbe)).isTrue();
	}

	@Test
	void aspectFailsClosedWhenNoTenantContextExists() {
		assertThatThrownBy(transactionProbe::currentSetting)
			.isInstanceOf(TenantContextRequiredException.class);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class ProbeConfiguration {

		@Bean
		public TransactionProbe transactionProbe(JdbcTemplate jdbcTemplate) {
			return new TransactionProbe(jdbcTemplate);
		}
	}

	public static class TransactionProbe {

		private final JdbcTemplate jdbcTemplate;

		public TransactionProbe(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
		}

		@TenantScopedTransactional(readOnly = true)
		public String currentSetting() {
			return jdbcTemplate.queryForObject("SELECT current_setting('app.tenant_id', true)", String.class);
		}
	}
}
