package com.domanski.smsmodular.time.entity;

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
@Table(name = "work_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkDay {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "employee_id", nullable = false, updatable = false)
	private UUID employeeId;
	@Column(name = "work_date", nullable = false, updatable = false)
	private LocalDate workDate;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private WorkStatus status;
	@Column(nullable = false, length = 16, updatable = false)
	private String source;
	@Column(name = "sms_message_id", updatable = false)
	private UUID smsMessageId;
	@Column(name = "total_minutes", nullable = false)
	private int totalMinutes;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Version
	@Column(nullable = false)
	private long version;

	public WorkDay(UUID id, UUID tenantId, UUID employeeId, LocalDate workDate, int totalMinutes, Instant now) {
		this(id, tenantId, employeeId, workDate, totalMinutes, null, now);
	}

	public WorkDay(UUID id, UUID tenantId, UUID employeeId, LocalDate workDate, int totalMinutes,
			UUID smsMessageId, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.employeeId = employeeId;
		this.workDate = workDate;
		this.status = WorkStatus.ACTIVE;
		this.source = smsMessageId == null ? "MANUAL" : "SMS";
		this.smsMessageId = smsMessageId;
		this.totalMinutes = totalMinutes;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void correct(int totalMinutes, Instant now) {
		this.totalMinutes = totalMinutes;
		this.updatedAt = now;
	}

	public void cancel(Instant now) {
		this.status = WorkStatus.CANCELLED;
		this.updatedAt = now;
	}
}
