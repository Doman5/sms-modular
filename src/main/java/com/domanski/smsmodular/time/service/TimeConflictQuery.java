package com.domanski.smsmodular.time.service;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.time.entity.WorkStatus;
import com.domanski.smsmodular.time.repository.WorkDayRepository;

@Service
@RequiredArgsConstructor
public class TimeConflictQuery {
	private final WorkDayRepository days;

	@Transactional(readOnly = true)
	public boolean hasWork(UUID tenantId, UUID employeeId, LocalDate date) {
		return days.existsByTenantIdAndEmployeeIdAndWorkDateAndStatus(tenantId, employeeId, date,
				WorkStatus.ACTIVE);
	}
}
