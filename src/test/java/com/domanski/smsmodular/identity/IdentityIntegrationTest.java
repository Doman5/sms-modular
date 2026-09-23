package com.domanski.smsmodular.identity;

import java.util.Base64;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.CreateUserRequest;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateAddonRequest;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateLimitRequest;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.identity.service.UserService;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantResponse;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.entity.PlatformAccount;
import com.domanski.smsmodular.identity.repository.PlatformAccountRepository;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.identity.security.TokenService;
import com.domanski.smsmodular.identity.service.TenantProvisioningService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.tenancy.repository.TenantRepository;
import com.domanski.smsmodular.tenancy.dto.UpdateTenantRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class IdentityIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("sms_modular_test");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.liquibase.user", postgres::getUsername);
		registry.add("spring.liquibase.password", postgres::getPassword);
		registry.add("app.security.jwt-secret-base64", () -> Base64.getEncoder()
				.encodeToString("identity-integration-secret-32-bytes-long".getBytes()));
		registry.add("app.security.bootstrap-platform-email", () -> "platform@example.test");
		registry.add("app.security.bootstrap-platform-password", () -> "platform-first-password");
	}

	@Autowired MockMvc mvc;
	@Autowired TenantProvisioningService provisioning;
	@Autowired UserAccountRepository users;
	@Autowired PlatformAccountRepository platformAccounts;
	@Autowired TokenService tokens;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtEncoder encoder;
	@Autowired com.domanski.smsmodular.tenancy.service.TenantService tenants;
	@Autowired TenantRepository tenantRepository;
	@Autowired EntitlementService entitlements;
	@Autowired UserService userService;

	@Test
	void temporaryPasswordMustBeChangedAndOldTokenIsRevoked() throws Exception {
		ProvisionTenantResponse tenant = provision();
		String email = tenant.firstAdmin().email();
		String temporary = tenant.temporaryPassword();
		String login = "{\"email\":\"" + email + "\",\"password\":\"" + temporary + "\"}";
		String token = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/v1/auth/login")
						.contentType("application/json").content(login)).andExpect(status().isOk())
						.andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn()
						.getResponse().getContentAsString(), "$.accessToken");
		mvc.perform(post("/api/v1/auth/login").contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"" + temporary + "x".repeat(80) + "\"}"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID"));
		mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + token)
					.contentType("application/json")
					.content("{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"new-strong-password-123\"}"))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/context").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/login").contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"new-strong-password-123\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void lastAdminAndTenantIsolationAreEnforced() throws Exception {
		ProvisionTenantResponse first = provision();
		ProvisionTenantResponse second = provision();
		String token = activeToken(first);
		mvc.perform(put("/api/v1/users/{id}/status", first.firstAdmin().id())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content("{\"status\":\"DISABLED\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
		mvc.perform(put("/api/v1/users/{id}", second.firstAdmin().id())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content("{\"displayName\":\"Changed\",\"roleId\":\"" + second.firstAdmin().roleId() + "\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/users/{id}", first.firstAdmin().id())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content("{\"displayName\":\"Changed\",\"roleId\":\"" + second.firstAdmin().roleId() + "\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void fiveFailuresLockTheAccountWithGenericError() throws Exception {
		ProvisionTenantResponse tenant = provision();
		String email = tenant.firstAdmin().email();
		for (int index = 0; index < 5; index++) {
			mvc.perform(post("/api/v1/auth/login").contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"incorrect-password\"}"))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID"));
		}
		mvc.perform(post("/api/v1/auth/login").contentType("application/json")
					.content("{\"email\":\"" + email + "\",\"password\":\"" + tenant.temporaryPassword() + "\"}"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID"));
		assertThat(users.findByNormalizedEmail(email).orElseThrow().getLockedUntil()).isNotNull();
	}

	@Test
	void platformBootstrapRequiresPasswordChangeBeforeProvisioning() throws Exception {
		String login = "{\"email\":\"platform@example.test\",\"password\":\"platform-first-password\"}";
		String token = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/platform/v1/auth/login")
				.contentType("application/json").content(login)).andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true)).andReturn()
				.getResponse().getContentAsString(), "$.accessToken");
		mvc.perform(get("/api/platform/v1/tenants").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/platform/v1/auth/change-password")
					.header("Authorization", "Bearer " + token).contentType("application/json")
					.content("{\"currentPassword\":\"platform-first-password\",\"newPassword\":\"platform-new-password-123\"}"))
				.andExpect(status().isNoContent());
		String nextToken = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/platform/v1/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"platform@example.test\",\"password\":\"platform-new-password-123\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.accessToken");
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/api/platform/v1/tenants").header("Authorization", "Bearer " + nextToken)
					.contentType("application/json")
					.content("{\"slug\":\"api-" + suffix + "\",\"name\":\"API tenant\",\"timeZone\":\"Europe/Warsaw\",\"locale\":\"pl-PL\",\"adminEmail\":\"api-" + suffix + "@example.test\",\"adminDisplayName\":\"First admin\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.firstAdmin.email").exists())
				.andExpect(jsonPath("$.temporaryPassword").isString());
	}

	@Test
	void tenantListAcceptsMissingSearchFilter() {
		ProvisionTenantResponse tenant = provision();

		var page = tenants.list(null, null, PageRequest.of(0, 100));

		assertThat(page.content()).anyMatch(response -> response.id().equals(tenant.tenant().id()));
	}

	@Test
	void roleChangesCannotRemoveLastAdministratorAndPermissionsAreEnforced() throws Exception {
		ProvisionTenantResponse tenant = provision();
		String token = activeToken(tenant);
		String role = mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"code\":\"READER\",\"name\":\"Reader\",\"permissions\":[\"USER_READ\"]}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String roleId = com.jayway.jsonpath.JsonPath.read(role, "$.id");
		mvc.perform(put("/api/v1/roles/{id}", tenant.firstAdmin().roleId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content("{\"name\":\"Owner\",\"permissions\":[\"USER_READ\"]}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String created = mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"email\":\"reader-" + suffix + "@example.test\",\"displayName\":\"Reader\",\"roleId\":\"" + roleId + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		UUID readerId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.user.id"));
		UserAccount reader = users.findByTenantIdAndId(tenant.tenant().id(), readerId).orElseThrow();
		reader.setMustChangePassword(false);
		users.save(reader);
		String readerToken = tokens.issue(reader).accessToken();
		mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + readerToken))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + readerToken))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + readerToken)
				.contentType("application/json")
				.content("{\"email\":\"new-" + suffix + "@example.test\",\"displayName\":\"New User\",\"roleId\":\"" + roleId + "\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/roles/{id}", roleId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"name\":\"Reader\",\"permissions\":[]}"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + readerToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void migrationsAndExpiredTokensAreRejected() throws Exception {
		Integer foreignKeys = jdbc.queryForObject("select count(*) from information_schema.table_constraints where table_name = 'user_accounts' and constraint_type = 'FOREIGN KEY'", Integer.class);
		assertThat(foreignKeys).isGreaterThanOrEqualTo(2);
		mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
		java.time.Instant now = java.time.Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TokenService.ISSUER)
				.audience(java.util.List.of(TokenService.AUDIENCE)).subject(UUID.randomUUID().toString())
				.issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600))
				.claim("kind", "tenant").claim("tenant_id", UUID.randomUUID().toString())
				.claim("session_version", 0).build();
		String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		mvc.perform(get("/api/v1/me/context").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void suspendedTenantCannotUseExistingToken() throws Exception {
		ProvisionTenantResponse tenant = provision();
		String token = activeToken(tenant);
		mvc.perform(get("/api/v1/me/context").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		tenants.suspend(tenant.tenant().id(), AuditCallContext.system("test-suspend"));
		mvc.perform(get("/api/v1/me/context").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void failedProvisioningRollsBackTheTenantAndRole() {
		ProvisionTenantResponse existing = provision();
		String slug = "rollback-" + UUID.randomUUID().toString().substring(0, 8);
		assertThatThrownBy(() -> provisioning.provision(new ProvisionTenantRequest(slug, "Rollback",
				"Europe/Warsaw", "pl-PL", existing.firstAdmin().email(), "Administrator"),
				AuditCallContext.system("test-rollback")))
				.isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.CONFLICT));
		assertThat(tenantRepository.existsBySlug(slug)).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from audit_entries where tenant_id is null and action_code = 'TENANT_PROVISIONED'", Integer.class)).isZero();
	}

	@Test
	void auditIsTenantScopedFilteredAndDoesNotExposeCredentials() throws Exception {
		ProvisionTenantResponse first = provision();
		ProvisionTenantResponse second = provision();
		String firstToken = activeToken(first);
		String secondToken = activeToken(second);
		assertThat(jdbc.queryForObject("select count(*) from role_permissions where role_id = ? and permission_code = 'AUDIT_READ'",
				Integer.class, first.firstAdmin().roleId())).isEqualTo(1);
		mvc.perform(post("/api/v1/auth/login").contentType("application/json")
				.content("{\"email\":\"" + first.firstAdmin().email() + "\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/auth/login").contentType("application/json")
				.content("{\"email\":\"unknown-" + UUID.randomUUID() + "@example.test\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + firstToken)
				.param("result", "DENIED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].action").value("LOGIN"))
				.andExpect(jsonPath("$.content[0].actorId").value(first.firstAdmin().id().toString()))
				.andExpect(jsonPath("$.content[0].metadata").isEmpty());
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + secondToken)
				.param("result", "DENIED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + firstToken)
				.param("action", "TENANT_PROVISIONED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].tenantId").value(first.tenant().id().toString()));
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + firstToken)
				.param("from", "2099-01-01T00:00:00Z"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUDIT_FILTER_INVALID"));
		mvc.perform(get("/api/v1/audit-logs"))
				.andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("select metadata::text from audit_entries where tenant_id = ? and action_code = 'LOGIN' and result = 'DENIED'",
				String.class, first.tenant().id())).doesNotContain("wrong-password").doesNotContain(first.firstAdmin().email());
	}

	@Test
	void platformAuditRequiresExplicitScopeAndTenantAuditCannotBeWrittenThroughApi() throws Exception {
		ProvisionTenantResponse tenant = provision();
		mvc.perform(get("/api/platform/v1/audit-logs").with(user("platform")
				.authorities(new SimpleGrantedAuthority("PLATFORM_ACCESS"), new SimpleGrantedAuthority("PLATFORM_AUDIT_READ"))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUDIT_SCOPE_INVALID"));
		mvc.perform(get("/api/platform/v1/audit-logs").with(user("platform")
				.authorities(new SimpleGrantedAuthority("PLATFORM_ACCESS"), new SimpleGrantedAuthority("PLATFORM_AUDIT_READ")))
				.param("scope", "global").param("tenantId", tenant.tenant().id().toString()))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/platform/v1/audit-logs").with(user("platform")
				.authorities(new SimpleGrantedAuthority("PLATFORM_ACCESS"), new SimpleGrantedAuthority("PLATFORM_AUDIT_READ")))
				.param("scope", "global"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").value("PLATFORM_BOOTSTRAPPED"));
		mvc.perform(get("/api/platform/v1/audit-logs").with(user("platform")
				.authorities(new SimpleGrantedAuthority("PLATFORM_ACCESS"), new SimpleGrantedAuthority("PLATFORM_AUDIT_READ")))
				.param("tenantId", tenant.tenant().id().toString()).param("module", "TENANCY"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").value("TENANT_PROVISIONED"));
		mvc.perform(post("/api/v1/audit-logs").header("Authorization", "Bearer " + activeToken(tenant)))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void failedAuditWriteRollsBackTenantChange() {
		ProvisionTenantResponse tenant = provision();
		UUID id = tenant.tenant().id();
		assertThatThrownBy(() -> tenants.update(id,
				new UpdateTenantRequest("Changed name", "Europe/Warsaw", "pl-PL"),
				AuditCallContext.system("invalid correlation id")))
				.isInstanceOf(ApiException.class);
		assertThat(tenants.get(id).name()).isEqualTo(tenant.tenant().name());
		assertThat(jdbc.queryForObject("select count(*) from audit_entries where tenant_id = ? and action_code = 'TENANT_UPDATED'",
				Integer.class, id)).isZero();
	}

	@Test
	void auditIncludesRoleAndTenantChangesInDescendingOrder() throws Exception {
		ProvisionTenantResponse tenant = provision();
		String token = activeToken(tenant);
		String role = mvc.perform(post("/api/v1/roles").header("Authorization", "Bearer " + token)
				.header("X-Correlation-Id", "role-created-test").contentType("application/json")
				.content("{\"code\":\"AUDITOR\",\"name\":\"Auditor\",\"permissions\":[\"AUDIT_READ\"]}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String roleId = com.jayway.jsonpath.JsonPath.read(role, "$.id");
		mvc.perform(put("/api/v1/roles/{id}", roleId).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"name\":\"Auditor changed\",\"permissions\":[]}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/tenant/settings").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"name\":\"Renamed tenant\",\"timeZone\":\"Europe/Warsaw\",\"locale\":\"pl-PL\"}"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].action").value("TENANT_UPDATED"))
				.andExpect(jsonPath("$.content[0].metadata.changedFields[0]").value("NAME"))
				.andExpect(jsonPath("$.content[1].action").value("ROLE_UPDATED"))
				.andExpect(jsonPath("$.content[1].metadata.removedPermissions[0]").value("AUDIT_READ"));
		mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + token)
				.param("action", "ROLE_CREATED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].correlationId").value("role-created-test"));
	}

	@Test
	void subscriptionIsScopedAndPlannedAddonsCannotBeEnabled() throws Exception {
		ProvisionTenantResponse first = provision();
		ProvisionTenantResponse second = provision();
		String firstToken = activeToken(first);
		assertThat(jdbc.queryForObject("select count(*) from role_permissions where role_id = ? and permission_code = 'SUBSCRIPTION_READ'",
				Integer.class, first.firstAdmin().roleId())).isEqualTo(1);
		mvc.perform(get("/api/v1/subscription").header("Authorization", "Bearer " + firstToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(first.tenant().id().toString()))
				.andExpect(jsonPath("$.planCode").value("BASE"))
				.andExpect(jsonPath("$.usage.metric").value("ACTIVE_USERS"))
				.andExpect(jsonPath("$.usage.used").value(1))
				.andExpect(jsonPath("$.usage.mode").value("UNLIMITED"))
				.andExpect(jsonPath("$.modules[?(@.key == 'PROJECTS')].availability").value("PLANNED"));
		mvc.perform(get("/api/v1/me/context").header("Authorization", "Bearer " + firstToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$.usage[0].used").value(1));
		mvc.perform(get("/api/v1/subscription").header("Authorization", "Bearer " + activeToken(second)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(second.tenant().id().toString()));
		assertThatThrownBy(() -> entitlements.updateAddon(first.tenant().id(), "PROJECTS",
				new UpdateAddonRequest(true, null, null), AuditCallContext.system("planned-addon")))
				.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getStatus())
						.isEqualTo(HttpStatus.CONFLICT));
		mvc.perform(get("/api/v1/subscription"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void activeUserLimitBlocksCreateAndReactivationButPreservesExistingAccounts() throws Exception {
		ProvisionTenantResponse tenant = provision();
		UUID tenantId = tenant.tenant().id();
		String token = activeToken(tenant);
		entitlements.updateActiveUserLimit(tenantId, new UpdateLimitRequest("FINITE", 2L, null),
				AuditCallContext.system("limit-two"));
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String created = mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"email\":\"limited-" + suffix + "@example.test\",\"displayName\":\"Limited\",\"roleId\":\""
						+ tenant.firstAdmin().roleId() + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String secondId = com.jayway.jsonpath.JsonPath.read(created, "$.user.id");
		entitlements.updateActiveUserLimit(tenantId, new UpdateLimitRequest("FINITE", 1L, null),
				AuditCallContext.system("limit-one"));
		mvc.perform(get("/api/v1/subscription").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.usage.used").value(2))
				.andExpect(jsonPath("$.usage.limit").value(1))
				.andExpect(jsonPath("$.usage.remaining").value(0));
		mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"email\":\"blocked-" + suffix + "@example.test\",\"displayName\":\"Blocked\",\"roleId\":\""
						+ tenant.firstAdmin().roleId() + "\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
		mvc.perform(put("/api/v1/users/{id}/status", secondId).header("Authorization", "Bearer " + token)
				.contentType("application/json").content("{\"status\":\"DISABLED\"}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/users/{id}/status", secondId).header("Authorization", "Bearer " + token)
				.contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
		assertThat(userService.activeCount(tenantId)).isEqualTo(1);
		entitlements.updateActiveUserLimit(tenantId, new UpdateLimitRequest("INHERIT", null, null),
				AuditCallContext.system("limit-inherit"));
		mvc.perform(put("/api/v1/users/{id}/status", secondId).header("Authorization", "Bearer " + token)
				.contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isOk());
		assertThat(userService.activeCount(tenantId)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from audit_entries where tenant_id = ? and action_code = 'LIMIT_UPDATED'",
				Integer.class, tenantId)).isEqualTo(3);
	}

	@Test
	void simultaneousUserCreationRespectsFiniteLimit() throws Exception {
		ProvisionTenantResponse tenant = provision();
		UUID tenantId = tenant.tenant().id();
		entitlements.updateActiveUserLimit(tenantId, new UpdateLimitRequest("FINITE", 2L, null),
				AuditCallContext.system("concurrency-limit"));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> pool.submit(() -> {
				ready.countDown();
				start.await();
				try {
					userService.create(tenantId, new CreateUserRequest("parallel-" + UUID.randomUUID() + "@example.test",
							"Parallel", tenant.firstAdmin().roleId()), AuditCallContext.system("parallel-" + index));
					return true;
				} catch (ApiException exception) {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					return false;
				}
			})).toList();
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(tasks.stream().filter(task -> {
				try { return task.get(15, TimeUnit.SECONDS); }
				catch (Exception exception) { throw new RuntimeException(exception); }
			}).count()).isEqualTo(1);
			assertThat(userService.activeCount(tenantId)).isEqualTo(2);
		} finally {
			start.countDown();
			pool.shutdownNow();
		}
	}

	@Test
	void platformSubscriptionCommandsRequirePermissionsAndValidateLimits() throws Exception {
		ProvisionTenantResponse tenant = provision();
		String platformToken = activePlatformToken();
		String tenantToken = activeToken(tenant);
		UUID tenantId = tenant.tenant().id();
		mvc.perform(get("/api/platform/v1/tenants/{tenantId}/subscription", tenantId)
				.header("Authorization", "Bearer " + tenantToken)).andExpect(status().isForbidden());
		mvc.perform(get("/api/platform/v1/tenants/{tenantId}/subscription", tenantId)
				.header("Authorization", "Bearer " + platformToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$.limitOverride.mode").value("INHERIT"));
		mvc.perform(put("/api/platform/v1/tenants/{tenantId}/subscription/limits/ACTIVE_USERS", tenantId)
				.header("Authorization", "Bearer " + tenantToken).contentType("application/json")
				.content("{\"mode\":\"FINITE\",\"value\":3}"))
				.andExpect(status().isForbidden());
		mvc.perform(put("/api/platform/v1/tenants/{tenantId}/subscription/limits/ACTIVE_USERS", tenantId)
				.header("Authorization", "Bearer " + platformToken).contentType("application/json")
				.content("{\"mode\":\"FINITE\",\"value\":0}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT_INVALID"));
		mvc.perform(put("/api/platform/v1/tenants/{tenantId}/subscription/limits/ACTIVE_USERS", tenantId)
				.header("Authorization", "Bearer " + platformToken).contentType("application/json")
				.content("{\"mode\":\"FINITE\",\"value\":3}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.usage.limit").value(3))
				.andExpect(jsonPath("$.limitOverride.value").value(3));
		mvc.perform(put("/api/platform/v1/tenants/{tenantId}/subscription/addons/PROJECTS", tenantId)
				.header("Authorization", "Bearer " + platformToken).contentType("application/json")
				.content("{\"enabled\":true}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODULE_NOT_AVAILABLE"));
	}

	@Test
	void addonDependencyAndPeriodsAreEnforced() {
		ProvisionTenantResponse tenant = provision();
		UUID tenantId = tenant.tenant().id();
		jdbc.update("update module_catalog set status = 'AVAILABLE' where key in ('PROJECTS', 'PLANNING')");
		try {
			assertThatThrownBy(() -> entitlements.updateAddon(tenantId, "PLANNING",
					new UpdateAddonRequest(true, null, null), AuditCallContext.system("planning-first")))
					.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
							.isEqualTo("ADDON_DEPENDENCY_REQUIRED"));
			entitlements.updateAddon(tenantId, "PROJECTS", new UpdateAddonRequest(true, null, null),
					AuditCallContext.system("projects-on"));
			entitlements.updateAddon(tenantId, "PLANNING", new UpdateAddonRequest(true, null, null),
					AuditCallContext.system("planning-on"));
			assertThat(entitlements.snapshot(tenantId).capabilities()).contains("PROJECTS", "PLANNING");
			assertThatThrownBy(() -> entitlements.updateAddon(tenantId, "PROJECTS",
					new UpdateAddonRequest(false, null, null), AuditCallContext.system("projects-off")))
					.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
							.isEqualTo("ADDON_DEPENDENCY_IN_USE"));
			assertThatThrownBy(() -> entitlements.updateAddon(tenantId, "PLANNING",
					new UpdateAddonRequest(true, Instant.now().plusSeconds(120), Instant.now().plusSeconds(60)),
					AuditCallContext.system("invalid-period")))
					.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
							.isEqualTo("ADDON_DATES_INVALID"));
			entitlements.updateAddon(tenantId, "PLANNING", new UpdateAddonRequest(false, null, null),
					AuditCallContext.system("planning-off"));
			entitlements.updateAddon(tenantId, "PROJECTS", new UpdateAddonRequest(false, null, null),
					AuditCallContext.system("projects-off-after"));
			assertThat(entitlements.snapshot(tenantId).capabilities()).doesNotContain("PROJECTS", "PLANNING");
		} finally {
			jdbc.update("update module_catalog set status = 'PLANNED' where key in ('PROJECTS', 'PLANNING')");
		}
	}

	private ProvisionTenantResponse provision() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return provisioning.provision(new ProvisionTenantRequest("tenant-" + suffix, "Tenant " + suffix,
				"Europe/Warsaw", "pl-PL", "admin-" + suffix + "@example.test", "Administrator"),
				AuditCallContext.system("test-provision-" + suffix));
	}

	private String activeToken(ProvisionTenantResponse tenant) {
		UserAccount user = users.findByTenantIdAndId(tenant.tenant().id(), tenant.firstAdmin().id()).orElseThrow();
		user.setMustChangePassword(false);
		users.save(user);
		return tokens.issue(user).accessToken();
	}

	private String activePlatformToken() {
		PlatformAccount account = new PlatformAccount(UUID.randomUUID(), "operator-" + UUID.randomUUID() + "@example.test",
				"not-used", Instant.now());
		account.setMustChangePassword(false);
		return tokens.issue(platformAccounts.save(account)).accessToken();
	}
}
