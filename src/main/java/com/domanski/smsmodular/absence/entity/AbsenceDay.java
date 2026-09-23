package com.domanski.smsmodular.absence.entity;

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
@Table(name = "absence_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbsenceDay {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "employee_id", nullable = false, updatable = false)
	private UUID employeeId;
	@Column(name = "absence_date", nullable = false, updatable = false)
	private LocalDate absenceDate;
	@Column(nullable = false, updatable = false, length = 16)
	private String source;
	@Column(name = "sms_message_id", updatable = false)
	private UUID smsMessageId;
	@Column(length = 2000)
	private String note;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AbsenceStatus status;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Version
	@Column(nullable = false)
	private long version;

	public AbsenceDay(UUID id, UUID tenantId, UUID employeeId, LocalDate date, String note, Instant now) {
		this(id, tenantId, employeeId, date, note, null, now);
	}

	public AbsenceDay(UUID id, UUID tenantId, UUID employeeId, LocalDate date, String note,
			UUID smsMessageId, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.employeeId = employeeId;
		this.absenceDate = date;
		this.source = smsMessageId == null ? "MANUAL" : "SMS";
		this.smsMessageId = smsMessageId;
		this.note = note;
		this.status = AbsenceStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void updateNote(String note, Instant now) {
		this.note = note;
		this.updatedAt = now;
	}

	public void cancel(Instant now) {
		this.status = AbsenceStatus.CANCELLED;
		this.updatedAt = now;
	}
}
