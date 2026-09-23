package com.domanski.smsmodular.time.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.domanski.smsmodular.time.entity.WorkDay;
import com.domanski.smsmodular.time.entity.WorkInterval;
import com.domanski.smsmodular.time.entity.WorkStatus;

public final class TimeDtos {
	private TimeDtos() {
	}

	public record IntervalInput(@NotNull LocalTime startTime, @NotNull LocalTime endTime) {
	}

	public record CreateWorkDayRequest(@NotNull LocalDate workDate,
			@NotEmpty List<@Valid IntervalInput> intervals) {
	}

	public record UpdateWorkDayRequest(@NotNull @PositiveOrZero Long version,
			@NotEmpty List<@Valid IntervalInput> intervals) {
	}

	public record VersionRequest(@NotNull @PositiveOrZero Long version) {
	}

	public record IntervalResponse(UUID id, LocalTime startTime, LocalTime endTime, int durationMinutes) {
		public static IntervalResponse from(WorkInterval interval) {
			return new IntervalResponse(interval.getId(), interval.getStartTime(), interval.getEndTime(),
					interval.getDurationMinutes());
		}
	}

	public record WorkDayResponse(UUID id, UUID employeeId, LocalDate workDate, WorkStatus status,
			String source, int totalMinutes, List<IntervalResponse> intervals,
			Instant createdAt, Instant updatedAt, long version) {
		public static WorkDayResponse from(WorkDay day, List<WorkInterval> intervals) {
			return new WorkDayResponse(day.getId(), day.getEmployeeId(), day.getWorkDate(), day.getStatus(),
					day.getSource(), day.getTotalMinutes(), intervals.stream().map(IntervalResponse::from).toList(),
					day.getCreatedAt(), day.getUpdatedAt(), day.getVersion());
		}
	}

	public record MonthSummary(String month, long dayCount, long totalMinutes) {
	}
}
