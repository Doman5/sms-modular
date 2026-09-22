package com.domanski.smsmodular.tenancy.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantPrincipal;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleService;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.domanski.smsmodular.support.FoundationTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FoundationTestConfiguration.class)
class TenantApiContractTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantRepository tenantStore;

	@Autowired
	private TenantLifecycleService tenantLifecycleService;

	private Tenant tenant;

	@BeforeEach
	void setUpTenant() {
		tenant = Tenant.create("api-" + UUID.randomUUID().toString().substring(0, 8), "API Tenant", "Europe/Warsaw", "pl-PL", CLOCK);
		tenantStore.save(tenant);
	}

	@Test
	void tenantGetAndPatchUsePrincipalContextAndNeverRequestTenantId() throws Exception {
		mockMvc.perform(get("/api/v1/tenant").with(authentication(tenantAuthentication(tenant.id()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(tenant.id().value().toString()))
			.andExpect(jsonPath("$.slug").value(tenant.slug()))
			.andExpect(jsonPath("$.timezone").value("Europe/Warsaw"));

		mockMvc.perform(patch("/api/v1/tenant")
				.with(authentication(tenantAuthentication(tenant.id(), "TENANT_SETTINGS_EDIT")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Updated API Tenant\",\"locale\":\"en-GB\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated API Tenant"))
			.andExpect(jsonPath("$.locale").value("en-GB"));
	}

	@Test
	void tenantPatchRequiresPermissionAndSuspendedTenantIsReadOnly() throws Exception {
		mockMvc.perform(patch("/api/v1/tenant")
				.with(authentication(tenantAuthentication(tenant.id())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Denied\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		tenant.suspend(CLOCK);
		tenantStore.save(tenant);
		mockMvc.perform(patch("/api/v1/tenant")
				.with(authentication(tenantAuthentication(tenant.id(), "TENANT_SETTINGS_EDIT")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Denied\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
	}

	@Test
	void platformLifecycleEndpointsUseGranularPermissionsAndImmutableSlug() throws Exception {
		String slug = "platform-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(post("/api/platform/v1/tenants")
				.with(user("platform").authorities(new SimpleGrantedAuthority("PLATFORM_TENANT_CREATE")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slug\":\"" + slug + "\",\"name\":\"Platform Tenant\",\"timezone\":\"UTC\",\"locale\":\"en\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(slug))
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(post("/api/platform/v1/tenants")
				.with(user("platform").authorities(new SimpleGrantedAuthority("PLATFORM_TENANT_CREATE")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slug\":\"" + slug.toUpperCase() + "\",\"name\":\"Duplicate\",\"timezone\":\"UTC\",\"locale\":\"en\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void missingOrInvalidTenantClaimFailsClosed() throws Exception {
		mockMvc.perform(get("/api/v1/tenant").with(user("without-tenant")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INVALID_TENANT_CONTEXT"));

		mockMvc.perform(get("/api/v1/tenant"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private UsernamePasswordAuthenticationToken tenantAuthentication(TenantId tenantId, String... authorities) {
		return new UsernamePasswordAuthenticationToken(
			new TenantPrincipal("api-user", tenantId),
			"n/a",
			List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
		);
	}
}
