package com.domanski.smsmodular.audit.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.audit.api.contract.AuditCommand;
import com.domanski.smsmodular.audit.api.contract.AuditPort;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantPrincipal;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import com.domanski.smsmodular.tenancy.api.contract.TenantContextRequiredException;
import com.domanski.smsmodular.support.FoundationTestConfiguration;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FoundationTestConfiguration.class)
class AuditApiContractTest {

	private static final Clock CLOCK = Clock.systemUTC();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantRepository tenantStore;

	@Autowired
	private AuditPort auditPort;

	private Tenant tenantA;
	private Tenant tenantB;

	@BeforeEach
	void setUpTenants() {
		tenantA = Tenant.create("audit-a-" + shortId(), "Audit A", "UTC", "en", CLOCK);
		tenantB = Tenant.create("audit-b-" + shortId(), "Audit B", "UTC", "en", CLOCK);
		tenantStore.save(tenantA);
		tenantStore.save(tenantB);
		TenantContext.runWith(tenantA.id(), () -> {
			auditPort.record(AuditCommand.tenant(
				tenantA.id(), null, "TENANCY", "SETTINGS_UPDATED", "TENANT", tenantA.id().value(),
				"SUCCESS", Instant.parse("2026-01-01T00:00:00Z"), "audit-a-1", Map.of("operation", "SETTINGS_UPDATED")
			));
			auditPort.record(AuditCommand.tenant(
				tenantA.id(), null, "TENANCY", "SUSPENDED", "TENANT", tenantA.id().value(),
				"SUCCESS", Instant.parse("2026-01-02T00:00:00Z"), "audit-a-2", Map.of("operation", "SUSPENDED")
			));
		});
		TenantContext.runWith(tenantB.id(), () -> auditPort.record(AuditCommand.tenant(
			tenantB.id(), null, "TENANCY", "SETTINGS_UPDATED", "TENANT", tenantB.id().value(),
			"SUCCESS", Instant.parse("2026-01-03T00:00:00Z"), "audit-b-1", Map.of("operation", "SETTINGS_UPDATED")
		)));
	}

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	void tenantReadIsPermissionProtectedTenantScopedAndSortedWithSafeMetadata() throws Exception {
		mockMvc.perform(get("/api/v1/audit-logs")
				.with(authentication(tenantAuthentication(tenantA.id(), "AUDIT_READ")))
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[0].action").value("SUSPENDED"))
			.andExpect(jsonPath("$.items[1].action").value("SETTINGS_UPDATED"))
			.andExpect(jsonPath("$.items[0].metadata.operation").value("SUSPENDED"))
			.andExpect(jsonPath("$.items[0].metadata.payload").doesNotExist())
			.andExpect(jsonPath("$.items[0].tenantId").value(tenantA.id().value().toString()));

		mockMvc.perform(get("/api/v1/audit-logs")
				.with(authentication(tenantAuthentication(tenantB.id(), "AUDIT_READ"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.items[0].tenantId").value(tenantB.id().value().toString()));

		mockMvc.perform(get("/api/v1/audit-logs")
				.with(authentication(tenantAuthentication(tenantA.id()))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void platformReadRequiresExplicitTenantFilterAndCanReadOnlyThatTenant() throws Exception {
		mockMvc.perform(get("/api/platform/v1/audit-logs")
				.with(user("platform").authorities(
					new SimpleGrantedAuthority("PLATFORM_OPERATOR"),
					new SimpleGrantedAuthority("PLATFORM_AUDIT_READ")
				))
				.param("tenantId", tenantA.id().value().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.items[*].tenantId").value(
				org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(tenantA.id().value().toString()))));

		mockMvc.perform(get("/api/platform/v1/audit-logs")
				.with(user("platform").authorities(
					new SimpleGrantedAuthority("PLATFORM_OPERATOR"),
					new SimpleGrantedAuthority("PLATFORM_AUDIT_READ")
				)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void recordFailsClosedWithoutTenantContext() {
		assertThatThrownBy(() -> auditPort.record(AuditCommand.tenant(
			tenantA.id(), null, "TENANCY", "SETTINGS_UPDATED", "TENANT", tenantA.id().value(),
			"SUCCESS", Instant.now(), "audit-no-context", Map.of()
		))).isInstanceOf(TenantContextRequiredException.class);
	}

	@Test
	void tenantEndpointNeverUsesAClientSuppliedTenantFilter() throws Exception {
		mockMvc.perform(get("/api/v1/audit-logs")
				.with(authentication(tenantAuthentication(tenantA.id(), "AUDIT_READ")))
				.param("tenantId", tenantB.id().value().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));
	}

	private UsernamePasswordAuthenticationToken tenantAuthentication(TenantId tenantId, String... authorities) {
		return new UsernamePasswordAuthenticationToken(
			new TenantPrincipal("audit-user", tenantId),
			"n/a",
			List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
		);
	}

	private String shortId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}
}
