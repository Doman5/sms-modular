package com.domanski.smsmodular.time.entity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_intervals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkInterval {
	@Id
	private UUID id;
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;
	@Column(name = "work_day_id", nullable = false, updatable = false)
	private UUID workDayId;
	@Column(name = "start_minute", nullable = false, updatable = false)
	private int startMinute;
	@Column(name = "end_minute", nullable = false, updatable = false)
	private int endMinute;
	@Column(name = "duration_minutes", nullable = false, updatable = false)
	private int durationMinutes;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private WorkStatus status;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public WorkInterval(UUID id, UUID tenantId, UUID workDayId, int startMinute,
			int endMinute, Instant now) {
		this.id = id;
		this.tenantId = tenantId;
		this.workDayId = workDayId;
		this.startMinute = startMinute;
		this.endMinute = endMinute;
		this.durationMinutes = endMinute - startMinute;
		this.status = WorkStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public LocalTime getStartTime() {
		return LocalTime.ofSecondOfDay(startMinute * 60L);
	}

	public LocalTime getEndTime() {
		return LocalTime.ofSecondOfDay((endMinute % 1440) * 60L);
	}

	public void cancel(Instant now) {
		this.status = WorkStatus.CANCELLED;
		this.updatedAt = now;
	}
}
