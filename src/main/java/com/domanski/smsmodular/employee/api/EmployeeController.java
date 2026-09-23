package com.domanski.smsmodular.employee.api;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.EmployeeResponse;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.EmployeeVersionRequest;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/employees")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class EmployeeController {
	private final EmployeeService employees;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('EMPLOYEE_READ')")
	public PageResponse<EmployeeResponse> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) EmployeeStatus status,
			@RequestParam(required = false) String position, Pageable pageable) {
		return employees.list(current.tenantId(), search, status, position, pageable);
	}

	@GetMapping("/positions")
	@PreAuthorize("hasAuthority('EMPLOYEE_READ')")
	public List<String> positions() {
		return employees.positions(current.tenantId());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('EMPLOYEE_READ')")
	public EmployeeResponse get(@PathVariable UUID id) {
		return employees.get(current.tenantId(), id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('EMPLOYEE_CREATE')")
	public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request,
			HttpServletRequest servletRequest) {
		return employees.create(current.tenantId(), request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
	public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest request,
			HttpServletRequest servletRequest) {
		return employees.update(current.tenantId(), id, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/activate")
	@PreAuthorize("hasAuthority('EMPLOYEE_STATUS_CHANGE')")
	public EmployeeResponse activate(@PathVariable UUID id, @Valid @RequestBody EmployeeVersionRequest request,
			HttpServletRequest servletRequest) {
		return employees.status(current.tenantId(), id, EmployeeStatus.ACTIVE, request.version(),
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasAuthority('EMPLOYEE_STATUS_CHANGE')")
	public EmployeeResponse deactivate(@PathVariable UUID id, @Valid @RequestBody EmployeeVersionRequest request,
			HttpServletRequest servletRequest) {
		return employees.status(current.tenantId(), id, EmployeeStatus.INACTIVE, request.version(),
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}
}
