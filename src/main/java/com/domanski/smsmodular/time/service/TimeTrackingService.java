package com.domanski.smsmodular.time.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.absence.service.AbsenceConflictQuery;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.time.dto.TimeDtos.CreateWorkDayRequest;
import com.domanski.smsmodular.time.dto.TimeDtos.IntervalInput;
import com.domanski.smsmodular.time.dto.TimeDtos.MonthSummary;
import com.domanski.smsmodular.time.dto.TimeDtos.UpdateWorkDayRequest;
import com.domanski.smsmodular.time.dto.TimeDtos.WorkDayResponse;
import com.domanski.smsmodular.time.entity.WorkDay;
import com.domanski.smsmodular.time.entity.WorkInterval;
import com.domanski.smsmodular.time.entity.WorkStatus;
import com.domanski.smsmodular.time.repository.WorkDayRepository;
import com.domanski.smsmodular.time.repository.WorkIntervalRepository;

@Service
@RequiredArgsConstructor
public class TimeTrackingService {
	private static final String CAPABILITY = "TIME_TRACKING";
	private static final Sort ORDER = Sort.by(Sort.Direction.DESC, "workDate", "id");
	private final WorkDayRepository days;
	private final WorkIntervalRepository intervals;
	private final EmployeeService employees;
	private final AbsenceConflictQuery absences;
	private final EntitlementService entitlements;
	private final AuditService audit;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<WorkDayResponse> list(UUID tenantId, LocalDate from, LocalDate to,
			UUID employeeId, Pageable pageable) {
		entitlements.require(tenantId, CAPABILITY);
		validateRange(from, to);
		Pageable fixed = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), ORDER);
		var page = employeeId == null
				? days.findByTenantIdAndStatusAndWorkDateBetween(tenantId, WorkStatus.ACTIVE, from, to, fixed)
				: days.findByTenantIdAndEmployeeIdAndStatusAndWorkDateBetween(tenantId, employeeId,
						WorkStatus.ACTIVE, from, to, fixed);
		return PageResponse.from(page.map(day -> response(tenantId, day)));
	}

	@Transactional(readOnly = true)
	public WorkDayResponse get(UUID tenantId, UUID dayId) {
		entitlements.require(tenantId, CAPABILITY);
		return response(tenantId, require(tenantId, dayId));
	}

	@Transactional(readOnly = true)
	public MonthSummary summary(UUID tenantId, String month, UUID employeeId) {
		entitlements.require(tenantId, CAPABILITY);
		YearMonth parsed;
		try {
			parsed = YearMonth.parse(month);
		} catch (DateTimeParseException | NullPointerException exception) {
			throw invalid("MONTH_INVALID", "Month must use YYYY-MM");
		}
		List<WorkDay> selected = employeeId == null
				? days.findByTenantIdAndStatusAndWorkDateBetween(tenantId, WorkStatus.ACTIVE,
						parsed.atDay(1), parsed.atEndOfMonth())
				: days.findByTenantIdAndEmployeeIdAndStatusAndWorkDateBetween(tenantId, employeeId,
						WorkStatus.ACTIVE, parsed.atDay(1), parsed.atEndOfMonth());
		return new MonthSummary(parsed.toString(), selected.size(),
				selected.stream().mapToLong(WorkDay::getTotalMinutes).sum());
	}

	@Transactional
	public WorkDayResponse create(UUID tenantId, UUID employeeId, CreateWorkDayRequest request,
			AuditCallContext context) {
		employees.lockForScheduleChange(tenantId, employeeId);
		entitlements.require(tenantId, CAPABILITY);
		if (request == null || request.workDate() == null) throw invalid("WORK_INTERVAL_INVALID", "Work day is invalid");
		List<IntervalInput> input = validated(request.intervals());
		if (absences.hasAbsence(tenantId, employeeId, request.workDate())) {
			throw conflict("ABSENCE_DAY_CONFLICT", "Employee is absent on this day");
		}
		if (days.existsByTenantIdAndEmployeeIdAndWorkDateAndStatus(tenantId, employeeId,
				request.workDate(), WorkStatus.ACTIVE)) {
			throw conflict("WORK_DAY_CONFLICT", "Work day already exists");
		}
		Instant now = clock.instant();
		WorkDay day = new WorkDay(UUID.randomUUID(), tenantId, employeeId, request.workDate(),
				total(input), now);
		try {
			days.saveAndFlush(day);
			intervals.saveAllAndFlush(newIntervals(tenantId, day.getId(), input, now));
		} catch (DataIntegrityViolationException exception) {
			throw conflict("WORK_DAY_CONFLICT", "Work day could not be created");
		}
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "WORK_DAY_CREATED",
				"WORK_DAY", day.getId(), Map.of()));
		return response(tenantId, day);
	}

	@Transactional
	public WorkDayResponse update(UUID tenantId, UUID employeeId, UUID dayId, UpdateWorkDayRequest request,
			AuditCallContext context) {
		employees.lockForScheduleChange(tenantId, employeeId);
		entitlements.require(tenantId, CAPABILITY);
		WorkDay day = requireForEmployee(tenantId, employeeId, dayId);
		if (request == null || request.version() == null) throw invalid("WORK_INTERVAL_INVALID", "Work day is invalid");
		checkVersion(day, request.version());
		if (day.getStatus() != WorkStatus.ACTIVE) throw conflict("WORK_DAY_CANCELLED", "Work day is cancelled");
		List<IntervalInput> input = validated(request.intervals());
		List<WorkInterval> previous = intervals.findByTenantIdAndWorkDayIdAndStatusOrderByStartMinuteAsc(
				tenantId, dayId, WorkStatus.ACTIVE);
		if (same(previous, input)) return response(tenantId, day);
		Instant now = clock.instant();
		previous.forEach(interval -> interval.cancel(now));
		intervals.saveAllAndFlush(newIntervals(tenantId, dayId, input, now));
		day.correct(total(input), now);
		days.flush();
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "WORK_DAY_CORRECTED",
				"WORK_DAY", dayId, Map.of("changedFields", List.of("INTERVALS"))));
		return response(tenantId, day);
	}

	@Transactional
	public WorkDayResponse cancel(UUID tenantId, UUID employeeId, UUID dayId, long version,
			AuditCallContext context) {
		employees.lockForScheduleChange(tenantId, employeeId);
		entitlements.require(tenantId, CAPABILITY);
		WorkDay day = requireForEmployee(tenantId, employeeId, dayId);
		checkVersion(day, version);
		if (day.getStatus() == WorkStatus.CANCELLED) return response(tenantId, day);
		day.cancel(clock.instant());
		days.flush();
		audit.record(AuditCommand.success(tenantId, context, CAPABILITY, "WORK_DAY_CANCELLED",
				"WORK_DAY", dayId, Map.of()));
		return response(tenantId, day);
	}

	private WorkDay require(UUID tenantId, UUID dayId) {
		return days.findByTenantIdAndId(tenantId, dayId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WORK_DAY_NOT_FOUND", "Work day was not found"));
	}

	private WorkDay requireForEmployee(UUID tenantId, UUID employeeId, UUID dayId) {
		WorkDay day = require(tenantId, dayId);
		if (!day.getEmployeeId().equals(employeeId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "WORK_DAY_NOT_FOUND", "Work day was not found");
		}
		return day;
	}

	private WorkDayResponse response(UUID tenantId, WorkDay day) {
		return WorkDayResponse.from(day, intervals.findByTenantIdAndWorkDayIdAndStatusOrderByStartMinuteAsc(
				tenantId, day.getId(), WorkStatus.ACTIVE));
	}

	private List<IntervalInput> validated(List<IntervalInput> input) {
		if (input == null || input.isEmpty() || input.size() > 32) {
			throw invalid("WORK_INTERVAL_INVALID", "At least one valid interval is required");
		}
		List<IntervalInput> sorted = new ArrayList<>(input);
		if (sorted.stream().anyMatch(item -> item == null || item.startTime() == null || item.endTime() == null
				|| endMinute(item) <= minute(item.startTime()) || item.startTime().getSecond() != 0
				|| item.endTime().getSecond() != 0 || item.startTime().getNano() != 0
				|| item.endTime().getNano() != 0)) {
			throw invalid("WORK_INTERVAL_INVALID", "Work interval is invalid");
		}
		sorted.sort(Comparator.comparing(IntervalInput::startTime));
		for (int index = 1; index < sorted.size(); index++) {
			if (minute(sorted.get(index).startTime()) < endMinute(sorted.get(index - 1))) {
				throw conflict("WORK_INTERVAL_OVERLAP", "Work intervals overlap");
			}
		}
		return sorted;
	}

	private int total(List<IntervalInput> input) {
		return input.stream().mapToInt(item -> endMinute(item) - minute(item.startTime())).sum();
	}

	private List<WorkInterval> newIntervals(UUID tenantId, UUID dayId, List<IntervalInput> input, Instant now) {
		return input.stream().map(item -> new WorkInterval(UUID.randomUUID(), tenantId, dayId,
				minute(item.startTime()), endMinute(item), now)).toList();
	}

	private int minute(LocalTime time) {
		return time.getHour() * 60 + time.getMinute();
	}

	private int endMinute(IntervalInput item) {
		return item.endTime().equals(LocalTime.MIDNIGHT) && !item.startTime().equals(LocalTime.MIDNIGHT)
				? 1440 : minute(item.endTime());
	}

	private boolean same(List<WorkInterval> existing, List<IntervalInput> input) {
		if (existing.size() != input.size()) return false;
		for (int index = 0; index < existing.size(); index++) {
			if (!existing.get(index).getStartTime().equals(input.get(index).startTime())
					|| !existing.get(index).getEndTime().equals(input.get(index).endTime())) return false;
		}
		return true;
	}

	private void validateRange(LocalDate from, LocalDate to) {
		if (from == null || to == null || from.isAfter(to) || from.plusDays(366).isBefore(to)) {
			throw invalid("WORK_DATE_RANGE_INVALID", "Date range is invalid");
		}
	}

	private void checkVersion(WorkDay day, long version) {
		if (day.getVersion() != version) throw conflict("WORK_DAY_VERSION_CONFLICT", "Work day was changed");
	}

	private ApiException invalid(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}
}
