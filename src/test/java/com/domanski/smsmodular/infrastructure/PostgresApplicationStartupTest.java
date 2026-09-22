package com.domanski.smsmodular.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.domanski.smsmodular.SmsModularApplication;
import com.domanski.smsmodular.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;


class PostgresApplicationStartupTest extends PostgresIntegrationTest {

	@Test
	void startsOnFreshDatabaseAndStartsAgainAfterLiquibaseHasRun() throws SQLException {
		try (ConfigurableApplicationContext first = startApplication()) {
			assertThat(first.isRunning()).isTrue();
			assertThat(dataSourceUrl(first)).isEqualTo(POSTGRES.getJdbcUrl());
			assertThat(schemaExists(first)).isTrue();
		}

		try (ConfigurableApplicationContext second = startApplication()) {
			assertThat(second.isRunning()).isTrue();
			assertThat(dataSourceUrl(second)).isEqualTo(POSTGRES.getJdbcUrl());
			assertThat(schemaExists(second)).isTrue();
		}
	}

	private ConfigurableApplicationContext startApplication() {
		return new SpringApplicationBuilder(SmsModularApplication.class)
			.web(WebApplicationType.SERVLET)
			.profiles("integration")
			.run(
				"--server.port=0",
				"--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(),
				"--spring.liquibase.user=" + POSTGRES.getUsername(),
				"--spring.liquibase.password=" + POSTGRES.getPassword(),
				"--spring.liquibase.enabled=true",
				"--spring.jpa.hibernate.ddl-auto=validate"
			);
	}

	private String dataSourceUrl(ConfigurableApplicationContext context) throws SQLException {
		DataSource dataSource = context.getBean(DataSource.class);
		try (Connection connection = dataSource.getConnection()) {
			return connection.getMetaData().getURL();
		}
	}

	private boolean schemaExists(ConfigurableApplicationContext context) {
		return Boolean.TRUE.equals(context.getBean(JdbcTemplate.class).queryForObject(
			"SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = ?)",
			Boolean.class,
			"foundation_control"
		));
	}
}
