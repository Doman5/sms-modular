package com.domanski.smsmodular.absence.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.AbsenceResponse;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.CalendarDay;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.CreateAbsenceRequest;
import com.domanski.smsmodular.absence.dto.AbsenceDtos.UpdateAbsenceRequest;
import com.domanski.smsmodular.absence.entity.AbsenceDay;
import com.domanski.smsmodular.absence.entity.AbsenceStatus;
import com.domanski.smsmodular.absence.repository.AbsenceDayRepository;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.time.service.TimeConflictQuery;

@Service
@RequiredArgsConstructor
public class AbsenceDayService {
	private static final String CAPABILITY = "ABSENCE_EVENTS";
	private static final Sort ORDER = Sort.by(Sort.Direction.DESC, "absenceDate", "id");
	private final AbsenceDayRepository days;
	private final EmployeeService employees;
	private final TimeConflictQuery time;
	private final EntitlementService entitlements;
	private final AuditService audit;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<AbsenceResponse> list(UUID tenantId, LocalDate from, LocalDate to,
			UUID employeeId, Pageable pageable) {
		entitlements.require(tenantId, CAPABILITY);
		validateRange(from, to);
		Pageable fixed = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), ORDER);
		var page = employeeId == null
				? days.findByTenantIdAndStatusAndAbsenceDateBetween(tenantId, AbsenceStatus.ACTIVE, from, to, fixed)
				: days.findByTenantIdAndEmployeeIdAndStatusAndAbsenceDateBetween(tenantId, employeeId,
						AbsenceStatus.ACTIVE, from, to, fixed);
		return PageResponse.from(page.map(AbsenceResponse::from));
	}

	@Transactional(readOnly = true)
	public AbsenceResponse get(UUID tenantId, UUID dayId) {
		entitlements.require(tenantId, CAPABILITY);
		return AbsenceResponse.from(require(tenantId, dayId));
	}

	@Transactional(readOnly = true)
	public List<CalendarDay> calendar(UUID tenantId, String month, UUID employeeId) {
		entitlements.require(tenantId, CAPABILITY);
		YearMonth parsed;
		try {
			parsed = YearMonth.parse(month);
		} catch (DateTimeParseException | NullPointerException exception) {
			throw invalid("MONTH_INVALID", "Month must use YYYY-MM");
		}
		List<AbsenceDay> selected = employeeId == null
				? days.findByTenantIdAndStatusAndAbsenceDateBetween(tenantId, AbsenceStatus.ACTIVE,
						parsed.atDay(1), parsed.atEndOfMonth())
				: days.findByTenantIdAndEmployeeIdAndStatusAndAbsenceDateBetween(tenantId, employeeId,
						AbsenceStatus.ACTIVE, parsed.atDay(1), parsed.atEndOfMonth());
		return selected.stream().collect(java.util.stream.Collectors.groupingBy(AbsenceDay::getAbsenceDate,
				java.util.TreeMap::new, java.util.stream.Collectors.counting())).entrySet().stream()
				.map(entry -> new CalendarDay(entry.getKey(), entry.getValue())).toList();
	}

	@Transactional
	public List<AbsenceResponse> create(UUID tenantId, CreateAbsenceRequest request, AuditCallContext context) {
		if (request == null || request.employeeId() == null) throw invalid("ABSENCE_RANGE_INVALID", "Absence is invalid");
		employees.lockForScheduleChange(tenantId, request.employeeId());
		entitlements.require(tenantId, CAPABILITY);
		validateRange(request.dateFrom(), request.dateTo());
		String note = normalizedNote(request.note());
		List<AbsenceDay> created = new ArrayList<>();
		Instant now = clock.instant();
		for (LocalDate date = request.dateFrom(); !date.isAfter(request.dateTo()); date = date.plusDays(1)) {
			if (days.existsByTenantIdAndEmployeeIdAndAbsenceDateAndStatus(tenantId, request.employeeId(),
					date, AbsenceStatus.ACTIVE)) {
				throw conflict("ABSENCE_DAY_CONFLICT", "Absence already exists on this day");
			}
			if (time.hasWork(tenantId, request.employeeId(), date)) {
				throw conflict("WORK_TIME_CONFLICT", "Work time exists on this day");
			}
			created.add(new AbsenceDay(UUID.randomUUID(), tenantId, request.employeeId(), date, note, now));
		}
		try {
			days.saveAllAndFlush(created);
		} catch (DataIntegrityViolationException exception) {
			throw conflict("ABSENCE_DAY_CONFLICT", "Absence could not be created");
		}
		for (AbsenceDay day : created) {
			audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "ABSENCE_DAY_CREATED",
					"ABSENCE_DAY", day.getId(), Map.of()));
		}
		return created.stream().map(AbsenceResponse::from).toList();
	}

	@Transactional
	public AbsenceResponse update(UUID tenantId, UUID dayId, UpdateAbsenceRequest request,
			AuditCallContext context) {
		employees.lockForScheduleChange(tenantId, employeeId(tenantId, dayId));
		entitlements.require(tenantId, CAPABILITY);
		AbsenceDay day = require(tenantId, dayId);
		if (request == null || request.version() == null) throw invalid("ABSENCE_INVALID", "Absence is invalid");
		checkVersion(day, request.version());
		if (day.getStatus() != AbsenceStatus.ACTIVE) throw conflict("ABSENCE_CANCELLED", "Absence is cancelled");
		String note = normalizedNote(request.note());
		if (Objects.equals(day.getNote(), note)) return AbsenceResponse.from(day);
		day.updateNote(note, clock.instant());
		days.flush();
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "ABSENCE_DAY_UPDATED",
				"ABSENCE_DAY", dayId, Map.of("changedFields", List.of("NOTE"))));
		return AbsenceResponse.from(day);
	}

	@Transactional
	public AbsenceResponse cancel(UUID tenantId, UUID dayId, long version, AuditCallContext context) {
		employees.lockForScheduleChange(tenantId, employeeId(tenantId, dayId));
		entitlements.require(tenantId, CAPABILITY);
		AbsenceDay day = require(tenantId, dayId);
		checkVersion(day, version);
		if (day.getStatus() == AbsenceStatus.CANCELLED) return AbsenceResponse.from(day);
		day.cancel(clock.instant());
		days.flush();
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "ABSENCE_DAY_CANCELLED",
				"ABSENCE_DAY", dayId, Map.of()));
		return AbsenceResponse.from(day);
	}

	private AbsenceDay require(UUID tenantId, UUID dayId) {
		return days.findByTenantIdAndId(tenantId, dayId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ABSENCE_DAY_NOT_FOUND", "Absence was not found"));
	}

	private UUID employeeId(UUID tenantId, UUID dayId) {
		return days.employeeId(tenantId, dayId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ABSENCE_DAY_NOT_FOUND", "Absence was not found"));
	}

	private void validateRange(LocalDate from, LocalDate to) {
		if (from == null || to == null || from.isAfter(to) || from.plusDays(365).isBefore(to)) {
			throw invalid("ABSENCE_RANGE_INVALID", "Date range is invalid");
		}
	}

	private String normalizedNote(String note) {
		if (note == null || note.isBlank()) return null;
		String normalized = note.trim();
		if (normalized.length() > 2000) throw invalid("ABSENCE_INVALID", "Absence note is too long");
		return normalized;
	}

	private void checkVersion(AbsenceDay day, long version) {
		if (day.getVersion() != version) throw conflict("ABSENCE_VERSION_CONFLICT", "Absence was changed");
	}

	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}
}
