package com.domanski.smsmodular.workforce;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import com.domanski.smsmodular.absence.dto.AbsenceDtos.CreateAbsenceRequest;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.UpdateAbsenceRequest;
import com.domanski.smsmodular.absence.service.AbsenceDayService;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ProvisionTenantResponse;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.identity.security.TokenService;
import com.domanski.smsmodular.identity.service.TenantProvisioningService;
import com.domanski.smsmodular.time.dto.TimeDtos.CreateWorkDayRequest;
import com.domanski.smsmodular.time.dto.TimeDtos.IntervalInput;
import com.domanski.smsmodular.time.dto.TimeDtos.UpdateWorkDayRequest;
import com.domanski.smsmodular.time.service.TimeTrackingService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WorkforceIntegrationTest {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
			.withDatabaseName("sms_modular_workforce_test");

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
		registry.add("spring.liquibase.user", postgres::getUsername);
		registry.add("spring.liquibase.password", postgres::getPassword);
		registry.add("app.security.jwt-secret-base64", () -> Base64.getEncoder()
				.encodeToString("workforce-integration-secret-32-bytes-long".getBytes()));
	}

	@Autowired TenantProvisioningService provisioning;
	@Autowired EmployeeService employees;
	@Autowired TimeTrackingService time;
	@Autowired AbsenceDayService absences;
	@Autowired UserAccountRepository users;
	@Autowired TokenService tokens;
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mvc;

	@Test
	void timeCorrectionAndCancellationPreserveHistory() {
		UUID tenantId = provision().tenant().id();
		UUID employeeId = employee(tenantId, "601234567");
		LocalDate date = LocalDate.of(2025, 4, 17);
		var created = time.create(tenantId, employeeId, work(date, "08:00", "12:00"), context());
		assertThat(created.totalMinutes()).isEqualTo(240);
		assertThatThrownBy(() -> time.create(tenantId, employeeId, work(date, "13:00", "16:00"), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertCode(error, "WORK_DAY_CONFLICT"));
		assertThatThrownBy(() -> time.update(tenantId, employeeId, created.id(),
				new UpdateWorkDayRequest(created.version(), List.of(interval("08:00", "12:00"),
						interval("11:00", "14:00"))), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertCode(error, "WORK_INTERVAL_OVERLAP"));
		var corrected = time.update(tenantId, employeeId, created.id(),
				new UpdateWorkDayRequest(created.version(), List.of(interval("08:00", "12:00"),
						interval("12:00", "15:00"))), context());
		assertThat(corrected.totalMinutes()).isEqualTo(420);
		assertThat(corrected.version()).isGreaterThan(created.version());
		assertThat(time.summary(tenantId, "2025-04", employeeId).totalMinutes()).isEqualTo(420);
		assertThatThrownBy(() -> time.cancel(tenantId, employeeId, created.id(), created.version(), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertCode(error, "WORK_DAY_VERSION_CONFLICT"));
		var cancelled = time.cancel(tenantId, employeeId, created.id(), corrected.version(), context());
		assertThat(time.summary(tenantId, "2025-04", employeeId).totalMinutes()).isZero();
		assertThat(time.create(tenantId, employeeId, work(date, "09:00", "10:00"), context()).id())
				.isNotEqualTo(cancelled.id());
		assertThat(jdbc.queryForObject("select count(*) from work_intervals where tenant_id = ? and work_day_id = ?",
				Long.class, tenantId, created.id())).isEqualTo(3);
	}

	@Test
	void absenceRangeIsAtomicAndConflictsWithWorkInBothDirections() {
		UUID tenantId = provision().tenant().id();
		UUID employeeId = employee(tenantId, "602234567");
		LocalDate first = LocalDate.of(2025, 5, 1);
		LocalDate second = first.plusDays(1);
		time.create(tenantId, employeeId, work(second, "08:00", "16:00"), context());
		assertThatThrownBy(() -> absences.create(tenantId,
				new CreateAbsenceRequest(employeeId, first, second, null), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertCode(error, "WORK_TIME_CONFLICT"));
		assertThat(absences.list(tenantId, first, second, employeeId, PageRequest.of(0, 20)).totalElements()).isZero();
		var created = absences.create(tenantId, new CreateAbsenceRequest(employeeId, first, first, "  Wolne  "), context());
		assertThat(created).hasSize(1);
		assertThat(created.getFirst().note()).isEqualTo("Wolne");
		assertThatThrownBy(() -> time.create(tenantId, employeeId, work(first, "08:00", "12:00"), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertCode(error, "ABSENCE_DAY_CONFLICT"));
		var changed = absences.update(tenantId, created.getFirst().id(),
				new UpdateAbsenceRequest(created.getFirst().version(), "Zaktualizowano"), context());
		assertThatThrownBy(() -> absences.cancel(tenantId, changed.id(), created.getFirst().version(), context()))
				.isInstanceOf(ApiException.class).satisfies(error -> assertCode(error, "ABSENCE_VERSION_CONFLICT"));
		absences.cancel(tenantId, changed.id(), changed.version(), context());
		assertThat(time.create(tenantId, employeeId, work(first, "08:00", "12:00"), context()).id()).isNotNull();
		assertThat(absences.calendar(tenantId, "2025-05", employeeId)).isEmpty();
	}

	@Test
	void nightShiftIsSplitAtMidnightWithoutLosingMinutes() {
		UUID tenantId = provision().tenant().id();
		UUID employeeId = employee(tenantId, "605234567");
		LocalDate date = LocalDate.of(2025, 8, 3);
		var beforeMidnight = time.create(tenantId, employeeId, work(date, "22:00", "00:00"), context());
		var afterMidnight = time.create(tenantId, employeeId, work(date.plusDays(1), "00:00", "06:00"), context());
		assertThat(beforeMidnight.totalMinutes()).isEqualTo(120);
		assertThat(afterMidnight.totalMinutes()).isEqualTo(360);
		assertThat(time.summary(tenantId, "2025-08", employeeId).totalMinutes()).isEqualTo(480);
	}

	@Test
	void tenantIsolationPermissionsAndModuleRenameAreVisibleThroughApi() throws Exception {
		ProvisionTenantResponse first = provision();
		ProvisionTenantResponse second = provision();
		UUID employeeId = employee(first.tenant().id(), "603234567");
		var day = time.create(first.tenant().id(), employeeId,
				work(LocalDate.of(2025, 6, 5), "08:00", "12:00"), context());
		assertThatThrownBy(() -> time.get(second.tenant().id(), day.id())).isInstanceOf(ApiException.class)
				.satisfies(error -> assertCode(error, "WORK_DAY_NOT_FOUND"));
		String token = token(first);
		mvc.perform(get("/api/v1/work-days").param("from", "2025-06-01").param("to", "2025-06-30"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/work-days").param("from", "2025-06-01").param("to", "2025-06-30")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/v1/work-days").param("from", "2025-06-01").param("to", "2025-06-30")
				.header("Authorization", "Bearer " + token(second)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(post("/api/v1/absence-days").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content("{\"employeeId\":\"" + employeeId + "\",\"dateFrom\":\"2025-06-06\",\"dateTo\":\"2025-06-07\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2));
		assertThat(jdbc.queryForObject("select count(*) from audit_entries where tenant_id = ? and module_code = 'ABSENCE_EVENTS'",
				Long.class, first.tenant().id())).isGreaterThan(0);
		assertThat(jdbc.queryForObject("select count(*) from module_catalog where key = 'DETAILED_ABSENCES'",
				Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from module_catalog where key = 'LEAVE_MANAGEMENT'",
				Long.class)).isZero();
		jdbc.update("delete from role_permissions where tenant_id = ? and permission_code = 'TIME_READ'",
				first.tenant().id());
		mvc.perform(get("/api/v1/work-days").param("from", "2025-06-01").param("to", "2025-06-30")
				.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void simultaneousWorkAndAbsenceCannotBothBecomeActive() throws Exception {
		UUID tenantId = provision().tenant().id();
		UUID employeeId = employee(tenantId, "604234567");
		LocalDate date = LocalDate.of(2025, 7, 5);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var work = pool.submit(() -> {
				ready.countDown(); go.await();
				try { time.create(tenantId, employeeId, work(date, "08:00", "12:00"), context()); return true; }
				catch (ApiException exception) { return false; }
			});
			var absent = pool.submit(() -> {
				ready.countDown(); go.await();
				try { absences.create(tenantId, new CreateAbsenceRequest(employeeId, date, date, null), context()); return true; }
				catch (ApiException exception) { return false; }
			});
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			assertThat((work.get(15, TimeUnit.SECONDS) ? 1 : 0) + (absent.get(15, TimeUnit.SECONDS) ? 1 : 0))
					.isEqualTo(1);
		}
	}

	private ProvisionTenantResponse provision() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return provisioning.provision(new ProvisionTenantRequest("workforce-" + suffix, "Tenant " + suffix,
				"Europe/Warsaw", "pl-PL", "admin-" + suffix + "@example.test", "Administrator"), context());
	}

	private UUID employee(UUID tenantId, String phone) {
		return employees.create(tenantId, new CreateEmployeeRequest("Anna", "Nowak", phone, null,
				"Pracownik", null, LocalDate.of(2024, 1, 1), EmployeeStatus.ACTIVE), context()).id();
	}

	private CreateWorkDayRequest work(LocalDate date, String from, String to) {
		return new CreateWorkDayRequest(date, List.of(interval(from, to)));
	}

	private IntervalInput interval(String from, String to) {
		return new IntervalInput(LocalTime.parse(from), LocalTime.parse(to));
	}

	private String token(ProvisionTenantResponse tenant) {
		UserAccount user = users.findByTenantIdAndId(tenant.tenant().id(), tenant.firstAdmin().id()).orElseThrow();
		user.setMustChangePassword(false);
		users.save(user);
		return tokens.issue(user).accessToken();
	}

	private AuditCallContext context() {
		return AuditCallContext.system("workforce-integration-test");
	}

	private void assertCode(Throwable error, String code) {
		assertThat(((ApiException) error).getCode()).isEqualTo(code);
	}
}
