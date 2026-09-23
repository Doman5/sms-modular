package com.domanski.smsmodular.time.api;

import java.time.LocalDate;
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
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.time.dto.TimeDtos.CreateWorkDayRequest;
import com.domanski.smsmodular.time.dto.TimeDtos.MonthSummary;
import com.domanski.smsmodular.time.dto.TimeDtos.UpdateWorkDayRequest;
import com.domanski.smsmodular.time.dto.TimeDtos.VersionRequest;
import com.domanski.smsmodular.time.dto.TimeDtos.WorkDayResponse;
import com.domanski.smsmodular.time.service.TimeTrackingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TimeTrackingController {
	private final TimeTrackingService time;
	private final CurrentIdentity current;

	@GetMapping("/work-days")
	@PreAuthorize("hasAuthority('TIME_READ')")
	public PageResponse<WorkDayResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(required = false) UUID employeeId, Pageable pageable) {
		return time.list(current.tenantId(), from, to, employeeId, pageable);
	}

	@GetMapping("/work-days/summary")
	@PreAuthorize("hasAuthority('TIME_READ')")
	public MonthSummary summary(@RequestParam String month, @RequestParam(required = false) UUID employeeId) {
		return time.summary(current.tenantId(), month, employeeId);
	}

	@GetMapping("/work-days/{id}")
	@PreAuthorize("hasAuthority('TIME_READ')")
	public WorkDayResponse get(@PathVariable UUID id) {
		return time.get(current.tenantId(), id);
	}

	@PostMapping("/employees/{employeeId}/work-days")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('TIME_EDIT')")
	public WorkDayResponse create(@PathVariable UUID employeeId, @Valid @RequestBody CreateWorkDayRequest request,
			HttpServletRequest servletRequest) {
		return time.create(current.tenantId(), employeeId, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PutMapping("/employees/{employeeId}/work-days/{id}")
	@PreAuthorize("hasAuthority('TIME_EDIT')")
	public WorkDayResponse update(@PathVariable UUID employeeId, @PathVariable UUID id,
			@Valid @RequestBody UpdateWorkDayRequest request, HttpServletRequest servletRequest) {
		return time.update(current.tenantId(), employeeId, id, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/employees/{employeeId}/work-days/{id}/cancel")
	@PreAuthorize("hasAuthority('TIME_EDIT')")
	public WorkDayResponse cancel(@PathVariable UUID employeeId, @PathVariable UUID id,
			@Valid @RequestBody VersionRequest request, HttpServletRequest servletRequest) {
		return time.cancel(current.tenantId(), employeeId, id, request.version(),
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}
}
