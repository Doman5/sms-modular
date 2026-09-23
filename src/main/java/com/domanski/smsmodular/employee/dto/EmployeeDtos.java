package com.domanski.smsmodular.employee.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.domanski.smsmodular.employee.entity.Employee;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;

public final class EmployeeDtos {
	private EmployeeDtos() {
	}

	public record CreateEmployeeRequest(@NotBlank @Size(max = 100) String firstName,
			@NotBlank @Size(max = 100) String lastName,
			@NotBlank @Size(max = 30) String phone,
			@Email @Size(max = 150) String email,
			@NotBlank @Size(max = 120) String position,
			@Size(max = 2000) String note,
			@NotNull LocalDate employmentDate,
			EmployeeStatus status) {
	}

	public record UpdateEmployeeRequest(@NotBlank @Size(max = 100) String firstName,
			@NotBlank @Size(max = 100) String lastName,
			@NotBlank @Size(max = 30) String phone,
			@Email @Size(max = 150) String email,
			@NotBlank @Size(max = 120) String position,
			@Size(max = 2000) String note,
			@NotNull LocalDate employmentDate,
			@NotNull @PositiveOrZero Long version) {
	}

	public record EmployeeVersionRequest(@NotNull @PositiveOrZero Long version) {
	}

	public record EmployeeResponse(UUID id, String firstName, String lastName, String phone,
			String normalizedPhone, String email, String position, String note,
			EmployeeStatus status, LocalDate employmentDate, Instant createdAt,
			Instant updatedAt, long version) {
		public static EmployeeResponse from(Employee employee) {
			return new EmployeeResponse(employee.getId(), employee.getFirstName(), employee.getLastName(),
					employee.getPhoneDisplay(), employee.getNormalizedPhone(), employee.getEmail(),
					employee.getPosition(), employee.getNote(), employee.getStatus(),
					employee.getEmploymentDate(), employee.getCreatedAt(), employee.getUpdatedAt(),
					employee.getVersion());
		}
	}
}
