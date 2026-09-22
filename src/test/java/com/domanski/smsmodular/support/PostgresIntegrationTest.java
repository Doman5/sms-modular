package com.domanski.smsmodular.support;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;


@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

	@Container
	protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
		.withDatabaseName("sms_modular")
		.withUsername("sms_modular")
		.withPassword("sms_modular");
}
