package com.domanski.smsmodular.sms;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantResponse;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.identity.security.TokenService;
import com.domanski.smsmodular.identity.service.TenantProvisioningService;
import com.domanski.smsmodular.integrationruntime.service.IntegrationRuntimeService;
import com.domanski.smsmodular.sms.dto.SmsDtos.CreateRouteRequest;
import com.domanski.smsmodular.sms.dto.SmsDtos.ResolveRequest;
import com.domanski.smsmodular.sms.service.SmsInboundService;
import com.domanski.smsmodular.sms.service.SmsProcessingService;
import com.domanski.smsmodular.sms.service.SmsQueryService;
import com.domanski.smsmodular.sms.service.SmsRetentionService;
import com.domanski.smsmodular.sms.service.SmsRouteService;
import com.domanski.smsmodular.time.service.TimeTrackingService;
import com.domanski.smsmodular.absence.service.AbsenceDayService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SmsInboundIntegrationTest {
	private static final String DEVICE = "test-sms-device";
	private static final String SECRET = "test-sms-gate-signing-key";
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("sms_modular_sms_test");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.liquibase.user", postgres::getUsername);
		registry.add("spring.liquibase.password", postgres::getPassword);
		registry.add("app.security.jwt-secret-base64", () -> Base64.getEncoder()
				.encodeToString("sms-integration-secret-32-bytes-long-enough".getBytes(StandardCharsets.UTF_8)));
		registry.add("app.sms.enabled", () -> "true");
		registry.add("app.sms.worker-enabled", () -> "false");
		registry.add("app.sms.device-id", () -> DEVICE);
		registry.add("app.sms.signing-key", () -> SECRET);
		registry.add("app.sms.data-key-base64", () -> Base64.getEncoder()
				.encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
	}

	@Autowired TenantProvisioningService provisioning;
	@Autowired EmployeeService employees;
	@Autowired SmsRouteService routes;
	@Autowired SmsInboundService inbound;
	@Autowired IntegrationRuntimeService runtime;
	@Autowired SmsProcessingService processing;
	@Autowired SmsQueryService query;
	@Autowired SmsRetentionService retention;
	@Autowired TimeTrackingService time;
	@Autowired AbsenceDayService absences;
	@Autowired UserAccountRepository users;
	@Autowired TokenService tokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	@Test
	void signedWebhookIsIdempotentAndCreatesSplitNightWork() throws Exception {
		var tenant = provision();
		UUID employeeId = employee(tenant.tenant().id(), "601111111");
		String recipient = "+48" + randomNine();
		routes.create(new CreateRouteRequest(tenant.tenant().id(), recipient, 1), context());
		String body = event("2025-08-03 praca 22:00-06:00", "601111111", recipient, 1, Instant.now());
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		mvc.perform(post("/api/integrations/v1/sms/sms-gate/inbound").contentType("application/json")
				.header("X-Signature", signature(body, timestamp)).header("X-Timestamp", timestamp).content(body))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.created").value(true));
		mvc.perform(post("/api/integrations/v1/sms/sms-gate/inbound").contentType("application/json")
				.header("X-Signature", signature(body, timestamp)).header("X-Timestamp", timestamp).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false));
		for (var job : runtime.claim("sms-test")) processing.process(job.tenantId(), job.smsMessageId(), job.id(), "sms-test");
		assertThat(time.summary(tenant.tenant().id(), "2025-08", employeeId).totalMinutes()).isEqualTo(480);
		assertThat(jdbc.queryForObject("select count(*) from work_days where tenant_id = ? and source = 'SMS'",
				Long.class, tenant.tenant().id())).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from inbox_receipts where tenant_id = ?",
				Long.class, tenant.tenant().id())).isEqualTo(1);
		mvc.perform(get("/api/v1/sms").param("from", Instant.now().minusSeconds(3600).toString())
				.param("to", Instant.now().plusSeconds(3600).toString())
				.header("Authorization", "Bearer " + token(tenant)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
	}

	@Test
	void ambiguousAbsenceTypeGoesToReviewAndManualDecisionCreatesBasicAbsence() {
		var tenant = provision();
		UUID employeeId = employee(tenant.tenant().id(), "602222222");
		int sim = 2;
		routes.create(new CreateRouteRequest(tenant.tenant().id(), null, sim), context());
		String body = event("urlop 2025-09-10", "602222222", null, sim, Instant.now());
		String timestamp = now();
		var accepted = inbound.receive(body.getBytes(StandardCharsets.UTF_8), signature(body, timestamp), timestamp, "sms-test");
		for (var job : runtime.claim("sms-review-test")) processing.process(job.tenantId(), job.smsMessageId(), job.id(), "sms-review-test");
		var review = query.get(tenant.tenant().id(), accepted.id());
		assertThat(review.status()).isEqualTo("REVIEW_REQUIRED");
		assertThat(review.reviewReason()).isEqualTo("CATEGORY_NOT_SUPPORTED");
		var resolved = processing.resolve(tenant.tenant().id(), accepted.id(), new ResolveRequest(
				review.version(), "ABSENCE", employeeId, null, null, null, LocalDate.of(2025, 9, 10)), context());
		assertThat(resolved.status()).isEqualTo("COMPLETED");
		assertThat(absences.calendar(tenant.tenant().id(), "2025-09", employeeId)).hasSize(1);
		assertThatThrownBy(() -> processing.reparse(tenant.tenant().id(), accepted.id(),
				resolved.version(), context())).hasMessageContaining("cannot be reparsed");
	}

	@Test
	void invalidSignatureAndUnknownRouteAreRejectedWithoutTenantWrite() throws Exception {
		String body = event("nie bedzie mnie", "603333333", "+48999999999", 99, Instant.now());
		mvc.perform(post("/api/integrations/v1/sms/sms-gate/inbound").contentType("application/json")
				.header("X-Signature", "bad").header("X-Timestamp", now()).content(body))
				.andExpect(status().isUnauthorized());
		String timestamp = now();
		mvc.perform(post("/api/integrations/v1/sms/sms-gate/inbound").contentType("application/json")
				.header("X-Signature", signature(body, timestamp)).header("X-Timestamp", timestamp).content(body))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("SMS_ROUTE_UNKNOWN"));
	}

	@Test
	void generalAbsenceIsAutomaticAndConflictingWorkRequiresReview() {
		var tenant = provision();
		UUID employeeId = employee(tenant.tenant().id(), "605555555");
		routes.create(new CreateRouteRequest(tenant.tenant().id(), null, 4), context());
		String absenceBody = event("nie bedzie mnie 2025-10-11", "605555555", null, 4, Instant.now());
		String timestamp = now();
		var absenceMessage = inbound.receive(absenceBody.getBytes(StandardCharsets.UTF_8),
				signature(absenceBody, timestamp), timestamp, "sms-absence-test");
		for (var job : runtime.claim("sms-absence-test")) {
			processing.process(job.tenantId(), job.smsMessageId(), job.id(), "sms-absence-test");
		}
		assertThat(absences.calendar(tenant.tenant().id(), "2025-10", employeeId)).hasSize(1);
		assertThat(query.get(tenant.tenant().id(), absenceMessage.id()).status()).isEqualTo("COMPLETED");
		String workBody = event("2025-10-11 praca 08:00-16:00", "605555555", null, 4, Instant.now());
		timestamp = now();
		var workMessage = inbound.receive(workBody.getBytes(StandardCharsets.UTF_8),
				signature(workBody, timestamp), timestamp, "sms-conflict-test");
		for (var job : runtime.claim("sms-conflict-test")) {
			assertThatThrownBy(() -> processing.process(job.tenantId(), job.smsMessageId(), job.id(),
					"sms-conflict-test")).hasMessageContaining("absent");
			processing.reviewAfterConflict(job.tenantId(), job.smsMessageId(), job.id(), "sms-conflict-test");
		}
		assertThat(query.get(tenant.tenant().id(), workMessage.id()).reviewReason()).isEqualTo("CONFLICT");
	}

	@Test
	void tenantIsolationAndNinetyDayRetentionScrubSensitiveFields() {
		var first = provision();
		var second = provision();
		String recipient = "+48" + randomNine();
		routes.create(new CreateRouteRequest(first.tenant().id(), recipient, 3), context());
		String body = event("nie bedzie mnie", "604444444", recipient, 3,
				Instant.now().minusSeconds(91L * 86400));
		String timestamp = now();
		var accepted = inbound.receive(body.getBytes(StandardCharsets.UTF_8), signature(body, timestamp),
				timestamp, "sms-retention-test");
		assertThatThrownBy(() -> query.get(second.tenant().id(), accepted.id()))
				.hasMessageContaining("not found");
		retention.expire();
		var expired = query.get(first.tenant().id(), accepted.id());
		assertThat(expired.sender()).isNull();
		assertThat(expired.content()).isNull();
		assertThat(expired.status()).isEqualTo("EXPIRED");
	}

	private ProvisionTenantResponse provision() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return provisioning.provision(new ProvisionTenantRequest("sms-" + suffix, "Tenant " + suffix,
				"Europe/Warsaw", "pl-PL", "admin-" + suffix + "@example.test", "Administrator"), context());
	}

	private UUID employee(UUID tenantId, String phone) {
		return employees.create(tenantId, new CreateEmployeeRequest("Anna", "Nowak", phone, null,
				"Pracownik", null, LocalDate.of(2024, 1, 1), EmployeeStatus.ACTIVE), context()).id();
	}

	private String token(ProvisionTenantResponse tenant) {
		var user = users.findByTenantIdAndId(tenant.tenant().id(), tenant.firstAdmin().id()).orElseThrow();
		user.setMustChangePassword(false);
		users.save(user);
		return tokens.issue(user).accessToken();
	}

	private String event(String message, String sender, String recipient, int sim, Instant receivedAt) {
		return "{\"deviceId\":\"" + DEVICE + "\",\"event\":\"sms:received\",\"id\":\""
				+ UUID.randomUUID() + "\",\"payload\":{\"messageId\":\"" + UUID.randomUUID()
				+ "\",\"message\":\"" + message + "\",\"sender\":\"" + sender
				+ "\",\"recipient\":" + (recipient == null ? "null" : "\"" + recipient + "\"")
				+ ",\"simNumber\":" + sim + ",\"receivedAt\":\"" + receivedAt + "\"}}";
	}

	private String now() {
		return String.valueOf(Instant.now().getEpochSecond());
	}

	private String signature(String body, String timestamp) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal((body + timestamp).getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private String randomNine() {
		return String.valueOf(600000000 + Math.floorMod(UUID.randomUUID().hashCode(), 300000000));
	}

	private AuditCallContext context() {
		return AuditCallContext.system("sms-integration-test");
	}
}
