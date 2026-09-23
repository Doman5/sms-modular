package com.domanski.smsmodular.absence.service;

import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.absence.entity.AbsenceStatus;
import com.domanski.smsmodular.absence.repository.AbsenceDayRepository;

@Service
@RequiredArgsConstructor
public class AbsenceConflictQuery {
	private final AbsenceDayRepository days;

	@Transactional(readOnly = true)
	public boolean hasAbsence(UUID tenantId, UUID employeeId, LocalDate date) {
		return days.existsByTenantIdAndEmployeeIdAndAbsenceDateAndStatus(tenantId, employeeId, date,
				AbsenceStatus.ACTIVE);
	}
}
