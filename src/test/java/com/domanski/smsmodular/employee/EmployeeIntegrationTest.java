package com.domanski.smsmodular.employee;

import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateLimitRequest;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantResponse;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.identity.security.TokenService;
import com.domanski.smsmodular.identity.service.TenantProvisioningService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeIntegrationTest {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("sms_modular_employee_test");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.liquibase.user", postgres::getUsername);
		registry.add("spring.liquibase.password", postgres::getPassword);
		registry.add("app.security.jwt-secret-base64", () -> Base64.getEncoder()
				.encodeToString("employee-integration-secret-32-bytes-long".getBytes()));
	}

	@Autowired EmployeeService employees;
	@Autowired TenantProvisioningService provisioning;
	@Autowired EntitlementService entitlements;
	@Autowired UserAccountRepository users;
	@Autowired TokenService tokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	@Test
	void phoneIsNormalizedAndUniqueOnlyInsideTenant() {
		UUID first = provision().tenant().id();
		UUID second = provision().tenant().id();
		var created = employees.create(first, input("501 234 567", EmployeeStatus.ACTIVE), context());
		assertThat(created.normalizedPhone()).isEqualTo("+48501234567");
		assertThat(created.phone()).isEqualTo("501 234 567");
		assertThatThrownBy(() -> employees.create(first, input("+48 501-234-567", EmployeeStatus.ACTIVE), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
						.isEqualTo("EMPLOYEE_PHONE_CONFLICT"));
		assertThat(employees.create(second, input("501234567", EmployeeStatus.ACTIVE), context()).id()).isNotNull();
		assertThat(employees.list(first, "501", null, null, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
		assertThat(employees.list(second, null, null, null, PageRequest.of(0, 20)).totalElements()).isEqualTo(1);
		assertThatThrownBy(() -> employees.get(second, created.id())).isInstanceOf(ApiException.class)
				.satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("EMPLOYEE_NOT_FOUND"));
	}

	@Test
	void finiteLimitBlocksActiveCreationAndReactivation() {
		UUID tenantId = provision().tenant().id();
		entitlements.updateLimit(tenantId, EntitlementService.ACTIVE_EMPLOYEES,
				new UpdateLimitRequest("FINITE", 1L, null), context());
		var first = employees.create(tenantId, input("502123456", EmployeeStatus.ACTIVE), context());
		assertThatThrownBy(() -> employees.create(tenantId, input("503123456", EmployeeStatus.ACTIVE), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
						.isEqualTo("EMPLOYEE_LIMIT_EXCEEDED"));
		var inactive = employees.create(tenantId, input("503123456", EmployeeStatus.INACTIVE), context());
		assertThatThrownBy(() -> employees.status(tenantId, inactive.id(), EmployeeStatus.ACTIVE, inactive.version(), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
						.isEqualTo("EMPLOYEE_LIMIT_EXCEEDED"));
		employees.status(tenantId, first.id(), EmployeeStatus.INACTIVE, first.version(), context());
		assertThat(employees.status(tenantId, inactive.id(), EmployeeStatus.ACTIVE, inactive.version(), context()).status())
				.isEqualTo(EmployeeStatus.ACTIVE);
	}

	@Test
	void updateRequiresCurrentVersion() {
		UUID tenantId = provision().tenant().id();
		var employee = employees.create(tenantId, input("504123456", EmployeeStatus.ACTIVE), context());
		var updated = employees.update(tenantId, employee.id(), new UpdateEmployeeRequest("Jan", "Kowalski",
				"504123456", null, "Brygadzista", null, LocalDate.of(2024, 1, 1), employee.version()), context());
		assertThat(updated.version()).isGreaterThan(employee.version());
		assertThatThrownBy(() -> employees.update(tenantId, employee.id(), new UpdateEmployeeRequest("Jan", "Nowak",
				"504123456", null, "Brygadzista", null, LocalDate.of(2024, 1, 1), employee.version()), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).getCode())
						.isEqualTo("EMPLOYEE_VERSION_CONFLICT"));
	}

	@Test
	void apiUsesTenantPrincipalAndRejectsAnonymousAccess() throws Exception {
		ProvisionTenantResponse first = provision();
		ProvisionTenantResponse second = provision();
		String token = token(first);
		mvc.perform(get("/api/v1/employees")).andExpect(status().isUnauthorized());
		String body = "{\"firstName\":\"Jan\",\"lastName\":\"Nowak\",\"phone\":\"505123456\","
				+ "\"position\":\"Kierowca\",\"employmentDate\":\"2024-01-01\"}";
		mvc.perform(post("/api/v1/employees").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.normalizedPhone").value("+48505123456"));
		mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + token(second)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		assertThat(jdbc.queryForObject("select count(*) from audit_entries where tenant_id = ? and action_code = 'EMPLOYEE_CREATED'",
				Long.class, first.tenant().id())).isEqualTo(1);
		jdbc.update("delete from role_permissions where tenant_id = ? and permission_code = 'EMPLOYEE_READ'",
				first.tenant().id());
		mvc.perform(get("/api/v1/employees").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	private CreateEmployeeRequest input(String phone, EmployeeStatus status) {
		return new CreateEmployeeRequest("Jan", "Nowak", phone, null, "Kierowca", null,
				LocalDate.of(2024, 1, 1), status);
	}

	private ProvisionTenantResponse provision() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return provisioning.provision(new ProvisionTenantRequest("employee-" + suffix, "Tenant " + suffix,
				"Europe/Warsaw", "pl-PL", "admin-" + suffix + "@example.test", "Administrator"), context());
	}

	private String token(ProvisionTenantResponse tenant) {
		UserAccount user = users.findByTenantIdAndId(tenant.tenant().id(), tenant.firstAdmin().id()).orElseThrow();
		user.setMustChangePassword(false);
		users.save(user);
		return tokens.issue(user).accessToken();
	}

	private AuditCallContext context() {
		return AuditCallContext.system("employee-integration-test");
	}
}
