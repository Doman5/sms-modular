package com.domanski.smsmodular.absence.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.domanski.smsmodular.absence.entity.AbsenceDay;
import com.domanski.smsmodular.absence.entity.AbsenceStatus;

public final class AbsenceDtos {
	private AbsenceDtos() {
	}

	public record CreateAbsenceRequest(@NotNull UUID employeeId, @NotNull LocalDate dateFrom,
			@NotNull LocalDate dateTo, @Size(max = 2000) String note) {
	}

	public record UpdateAbsenceRequest(@NotNull @PositiveOrZero Long version,
			@Size(max = 2000) String note) {
	}

	public record VersionRequest(@NotNull @PositiveOrZero Long version) {
	}

	public record CalendarDay(LocalDate date, long count) {
	}

	public record AbsenceResponse(UUID id, UUID employeeId, LocalDate absenceDate, String source,
			String note, AbsenceStatus status, Instant createdAt, Instant updatedAt, long version) {
		public static AbsenceResponse from(AbsenceDay day) {
			return new AbsenceResponse(day.getId(), day.getEmployeeId(), day.getAbsenceDate(),
					day.getSource(), day.getNote(), day.getStatus(), day.getCreatedAt(), day.getUpdatedAt(),
					day.getVersion());
		}
	}
}
