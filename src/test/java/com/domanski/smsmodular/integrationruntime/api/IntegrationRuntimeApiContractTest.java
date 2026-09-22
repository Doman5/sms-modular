package com.domanski.smsmodular.integrationruntime.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.domanski.smsmodular.audit.api.contract.AuditCommand;
import com.domanski.smsmodular.audit.api.contract.AuditPort;
import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.OutboxRepository;
import com.domanski.smsmodular.support.FoundationTestConfiguration;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({FoundationTestConfiguration.class, IntegrationRuntimeApiContractTest.Mocks.class})
class IntegrationRuntimeApiContractTest {

	private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OutboxRepository repository;

	@Autowired
	private AuditPort auditPort;

	@Test
	void manualRetryIsPermissionProtectedSafeAndAuditedWithPlatformActor() throws Exception {
		UUID id = UUID.randomUUID();
		OutboxMessage dead = message(id, OutboxStatus.DEAD_LETTER, 8);
		OutboxMessage pending = message(id, OutboxStatus.PENDING, 0);
		when(repository.findById(id)).thenReturn(Optional.of(dead), Optional.of(pending));
		when(repository.manualRetry(any(), any(), any())).thenReturn(true);

		mockMvc.perform(post("/api/platform/v1/integration-runtime/dead-letters/{id}/retry", id)
				.with(user("11111111-1111-1111-1111-111111111111")
					.authorities(() -> "PLATFORM_INTEGRATION_RETRY"))
				.param("tenantId", TENANT.value().toString())
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.payload").doesNotExist())
			.andExpect(jsonPath("$.tenantId").value(TENANT.value().toString()));

		var command = org.mockito.ArgumentCaptor.forClass(AuditCommand.class);
		verify(auditPort).record(command.capture());
		org.assertj.core.api.Assertions.assertThat(command.getValue().tenantId()).isEqualTo(TENANT);
		org.assertj.core.api.Assertions.assertThat(command.getValue().actor().isPlatformActor()).isTrue();
		org.assertj.core.api.Assertions.assertThat(command.getValue().action()).isEqualTo("DEAD_LETTER_RETRIED");
	}

	@Test
	void readListsArePagedAndRetryPermissionIsSeparate() throws Exception {
		when(repository.findDeadLetters(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

		mockMvc.perform(get("/api/platform/v1/integration-runtime/dead-letters")
				.with(user("platform").authorities(() -> "PLATFORM_INTEGRATION_READ"))
				.param("tenantId", TENANT.value().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(post("/api/platform/v1/integration-runtime/dead-letters/{id}/retry", UUID.randomUUID())
				.with(user("platform").authorities(() -> "PLATFORM_INTEGRATION_READ"))
				.param("tenantId", TENANT.value().toString()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	private OutboxMessage message(UUID id, OutboxStatus status, int attempt) {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		return new OutboxMessage(
			id, TENANT, "TEST.EVENT", "TEST", UUID.randomUUID(), 1, "secret-payload",
			status, attempt, now, null, null, "api-correlation", "api-idempotency-" + id,
			now, now, status == OutboxStatus.DEAD_LETTER ? "HANDLER_FAILURE" : null
		);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Mocks {
		@Bean
		@Primary
		OutboxRepository mockOutboxRepository() {
			return mock(OutboxRepository.class);
		}

		@Bean
		@Primary
		AuditPort mockAuditPort() {
			return mock(AuditPort.class);
		}
	}
}
