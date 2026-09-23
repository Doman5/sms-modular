package com.domanski.smsmodular.employee.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {
	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;
	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;
	@Column(name = "normalized_phone", nullable = false, length = 16)
	private String normalizedPhone;
	@Column(name = "phone_display", nullable = false, length = 30)
	private String phoneDisplay;
	@Column(length = 150)
	private String email;
	@Column(nullable = false, length = 120)
	private String position;
	@Column(length = 2000)
	private String note;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private EmployeeStatus status;
	@Column(name = "employment_date", nullable = false)
	private LocalDate employmentDate;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Version
	@Column(nullable = false)
	private long version;

	public Employee(UUID id, UUID tenantId, String firstName, String lastName, String normalizedPhone,
			String phoneDisplay, String email, String position, String note,
			EmployeeStatus status, LocalDate employmentDate, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		update(firstName, lastName, normalizedPhone, phoneDisplay, email, position, note, employmentDate, now);
		this.status = status;
		this.createdAt = now;
	}

	public void update(String firstName, String lastName, String normalizedPhone, String phoneDisplay,
			String email, String position, String note, LocalDate employmentDate, Instant now) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.normalizedPhone = normalizedPhone;
		this.phoneDisplay = phoneDisplay;
		this.email = email;
		this.position = position;
		this.note = note;
		this.employmentDate = employmentDate;
		this.updatedAt = now;
	}

	public void setStatus(EmployeeStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}
}
