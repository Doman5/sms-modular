package com.domanski.smsmodular.absence.api;

import java.time.LocalDate;
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
import com.domanski.smsmodular.absence.dto.AbsenceDtos.AbsenceResponse;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.CreateAbsenceRequest;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.CalendarDay;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.UpdateAbsenceRequest;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.VersionRequest;
import com.domanski.smsmodular.absence.service.AbsenceDayService;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/absence-days")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AbsenceDayController {
	private final AbsenceDayService absences;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('ABSENCE_READ')")
	public PageResponse<AbsenceResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(required = false) UUID employeeId, Pageable pageable) {
		return absences.list(current.tenantId(), from, to, employeeId, pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('ABSENCE_READ')")
	public AbsenceResponse get(@PathVariable UUID id) {
		return absences.get(current.tenantId(), id);
	}

	@GetMapping("/calendar")
	@PreAuthorize("hasAuthority('ABSENCE_READ')")
	public List<CalendarDay> calendar(@RequestParam String month,
			@RequestParam(required = false) UUID employeeId) {
		return absences.calendar(current.tenantId(), month, employeeId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('ABSENCE_EDIT')")
	public List<AbsenceResponse> create(@Valid @RequestBody CreateAbsenceRequest request,
			HttpServletRequest servletRequest) {
		return absences.create(current.tenantId(), request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ABSENCE_EDIT')")
	public AbsenceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAbsenceRequest request,
			HttpServletRequest servletRequest) {
		return absences.update(current.tenantId(), id, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasAuthority('ABSENCE_EDIT')")
	public AbsenceResponse cancel(@PathVariable UUID id, @Valid @RequestBody VersionRequest request,
			HttpServletRequest servletRequest) {
		return absences.cancel(current.tenantId(), id, request.version(),
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}
}
