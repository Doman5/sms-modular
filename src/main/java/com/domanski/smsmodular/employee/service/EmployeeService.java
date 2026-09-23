package com.domanski.smsmodular.employee.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.EmployeeResponse;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.EmployeeOption;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.domanski.smsmodular.employee.entity.Employee;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;
import com.domanski.smsmodular.employee.repository.EmployeeRepository;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.tenancy.service.TenantService;
import com.domanski.smsmodular.usage.service.UsageService;

@Service
@RequiredArgsConstructor
public class EmployeeService {
	private static final String CAPABILITY = "EMPLOYEE_DIRECTORY";
	private static final Sort LIST_ORDER = Sort.by("lastName", "firstName", "id");
	private final EmployeeRepository employees;
	private final TenantService tenants;
	private final EntitlementService entitlements;
	private final UsageService usage;
	private final AuditService audit;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<EmployeeResponse> list(UUID tenantId, String search, EmployeeStatus status,
			String position, Pageable pageable) {
		entitlements.require(tenantId, CAPABILITY);
		String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		if (normalizedSearch.length() > 120) throw invalid("EMPLOYEE_SEARCH_INVALID", "Search is too long");
		String normalizedPosition = position == null || position.isBlank() ? null : position.trim();
		if (normalizedPosition != null && normalizedPosition.length() > 120) {
			throw invalid("EMPLOYEE_POSITION_INVALID", "Position is too long");
		}
		String digits = normalizedSearch.replaceAll("[^0-9+]", "");
		String phoneSearch = digits;
		Pageable fixed = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), LIST_ORDER);
		return PageResponse.from(employees.search(tenantId, status, normalizedPosition,
				normalizedSearch, phoneSearch, fixed).map(EmployeeResponse::from));
	}

	@Transactional(readOnly = true)
	public EmployeeResponse get(UUID tenantId, UUID employeeId) {
		entitlements.require(tenantId, CAPABILITY);
		return EmployeeResponse.from(require(tenantId, employeeId));
	}

	@Transactional(readOnly = true)
	public PageResponse<EmployeeOption> options(UUID tenantId, String search, Pageable pageable) {
		entitlements.require(tenantId, CAPABILITY);
		String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() > 120) throw invalid("EMPLOYEE_SEARCH_INVALID", "Search is too long");
		Pageable fixed = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), LIST_ORDER);
		return PageResponse.from(employees.search(tenantId, null, null, normalized, "", fixed)
				.map(EmployeeOption::from));
	}

	@Transactional(readOnly = true)
	public EmployeeOption option(UUID tenantId, UUID employeeId) {
		entitlements.require(tenantId, CAPABILITY);
		return EmployeeOption.from(require(tenantId, employeeId));
	}

	@Transactional(readOnly = true)
	public List<String> positions(UUID tenantId) {
		entitlements.require(tenantId, CAPABILITY);
		return employees.positions(tenantId);
	}

	@Transactional(readOnly = true)
	public long activeCount(UUID tenantId) {
		return employees.countByTenantIdAndStatus(tenantId, EmployeeStatus.ACTIVE);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockForScheduleChange(UUID tenantId, UUID employeeId) {
		employees.lockByTenantIdAndId(tenantId, employeeId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "Employee was not found"));
	}

	@Transactional
	public EmployeeResponse create(UUID tenantId, CreateEmployeeRequest request, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		entitlements.require(tenantId, CAPABILITY);
		if (request == null || request.employmentDate() == null) throw invalid("EMPLOYEE_INVALID", "Employee data is invalid");
		String firstName = required(request.firstName(), 100);
		String lastName = required(request.lastName(), 100);
		String phone = required(request.phone(), 30);
		String normalizedPhone = normalizePhone(phone);
		String position = required(request.position(), 120);
		String email = optional(request.email(), 150);
		String note = optional(request.note(), 2000);
		EmployeeStatus status = request.status() == null ? EmployeeStatus.ACTIVE : request.status();
		if (status == EmployeeStatus.ACTIVE) usage.requireAdditionalActiveEmployee(tenantId, activeCount(tenantId));
		UUID id = UUID.randomUUID();
		if (employees.existsByTenantIdAndNormalizedPhoneAndIdNot(tenantId, normalizedPhone, id)) throw phoneConflict();
		Instant now = clock.instant();
		Employee employee = new Employee(id, tenantId, firstName, lastName, normalizedPhone,
				phone, email, position, note, status, request.employmentDate(), now);
		try {
			employees.saveAndFlush(employee);
		} catch (DataIntegrityViolationException exception) {
			throw phoneConflict();
		}
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "EMPLOYEE_CREATED",
				"EMPLOYEE", id, Map.of()));
		return EmployeeResponse.from(employee);
	}

	@Transactional
	public EmployeeResponse update(UUID tenantId, UUID employeeId, UpdateEmployeeRequest request,
			AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		entitlements.require(tenantId, CAPABILITY);
		Employee employee = require(tenantId, employeeId);
		if (request == null || request.version() == null || request.employmentDate() == null) {
			throw invalid("EMPLOYEE_INVALID", "Employee data is invalid");
		}
		checkVersion(employee, request.version());
		String firstName = required(request.firstName(), 100);
		String lastName = required(request.lastName(), 100);
		String phone = required(request.phone(), 30);
		String normalizedPhone = normalizePhone(phone);
		String position = required(request.position(), 120);
		String email = optional(request.email(), 150);
		String note = optional(request.note(), 2000);
		List<String> changed = new ArrayList<>();
		if (!employee.getFirstName().equals(firstName)) changed.add("FIRST_NAME");
		if (!employee.getLastName().equals(lastName)) changed.add("LAST_NAME");
		if (!employee.getNormalizedPhone().equals(normalizedPhone)
				|| !employee.getPhoneDisplay().equals(phone)) changed.add("PHONE");
		if (!Objects.equals(employee.getEmail(), email)) changed.add("EMAIL");
		if (!employee.getPosition().equals(position)) changed.add("POSITION");
		if (!Objects.equals(employee.getNote(), note)) changed.add("NOTE");
		if (!employee.getEmploymentDate().equals(request.employmentDate())) changed.add("EMPLOYMENT_DATE");
		if (changed.isEmpty()) return EmployeeResponse.from(employee);
		if (employees.existsByTenantIdAndNormalizedPhoneAndIdNot(tenantId, normalizedPhone, employeeId)) throw phoneConflict();
		employee.update(firstName, lastName, normalizedPhone, phone, email, position, note,
				request.employmentDate(), clock.instant());
		try {
			employees.flush();
		} catch (DataIntegrityViolationException exception) {
			throw phoneConflict();
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw versionConflict();
		}
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "EMPLOYEE_UPDATED",
				"EMPLOYEE", employeeId, Map.of("changedFields", changed)));
		return EmployeeResponse.from(employee);
	}

	@Transactional
	public EmployeeResponse status(UUID tenantId, UUID employeeId, EmployeeStatus status, long version,
			AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		entitlements.require(tenantId, CAPABILITY);
		Employee employee = require(tenantId, employeeId);
		checkVersion(employee, version);
		if (employee.getStatus() == status) return EmployeeResponse.from(employee);
		if (status == EmployeeStatus.ACTIVE) usage.requireAdditionalActiveEmployee(tenantId, activeCount(tenantId));
		String previous = employee.getStatus().name();
		employee.setStatus(status, clock.instant());
		try {
			employees.flush();
		} catch (ObjectOptimisticLockingFailureException exception) {
			throw versionConflict();
		}
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "EMPLOYEE_STATUS_CHANGED",
				"EMPLOYEE", employeeId, Map.of("fromStatus", previous, "toStatus", status.name())));
		return EmployeeResponse.from(employee);
	}

	private Employee require(UUID tenantId, UUID employeeId) {
		return employees.findByTenantIdAndId(tenantId, employeeId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "Employee was not found"));
	}

	private void checkVersion(Employee employee, long version) {
		if (employee.getVersion() != version) throw versionConflict();
	}

	private String required(String value, int max) {
		String result = value == null ? "" : value.trim();
		if (result.isEmpty() || result.length() > max) throw invalid("EMPLOYEE_INVALID", "Employee data is invalid");
		return result;
	}

	private String optional(String value, int max) {
		if (value == null || value.isBlank()) return null;
		String result = value.trim();
		if (result.length() > max) throw invalid("EMPLOYEE_INVALID", "Employee data is invalid");
		return result;
	}

	private String normalizePhone(String phone) {
		String compact = phone.replaceAll("[\\s()\\-]", "");
		if (compact.matches("[1-9][0-9]{8}")) compact = "+48" + compact;
		if (!compact.matches("\\+[1-9][0-9]{7,14}")) {
			throw invalid("EMPLOYEE_PHONE_INVALID", "Phone must use E.164 or nine Polish digits");
		}
		return compact;
	}

	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	private ApiException phoneConflict() {
		return new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_PHONE_CONFLICT", "Employee phone is already in use");
	}

	private ApiException versionConflict() {
		return new ApiException(HttpStatus.CONFLICT, "EMPLOYEE_VERSION_CONFLICT", "Employee was changed by another user");
	}
}
