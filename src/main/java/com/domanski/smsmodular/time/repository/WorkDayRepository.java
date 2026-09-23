package com.domanski.smsmodular.time.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.time.entity.WorkDay;
import com.domanski.smsmodular.time.entity.WorkStatus;

public interface WorkDayRepository extends JpaRepository<WorkDay, UUID> {
	Optional<WorkDay> findByTenantIdAndId(UUID tenantId, UUID id);
	boolean existsByTenantIdAndEmployeeIdAndWorkDateAndStatus(UUID tenantId, UUID employeeId,
			LocalDate workDate, WorkStatus status);
	Page<WorkDay> findByTenantIdAndStatusAndWorkDateBetween(UUID tenantId, WorkStatus status,
			LocalDate from, LocalDate to, Pageable pageable);
	Page<WorkDay> findByTenantIdAndEmployeeIdAndStatusAndWorkDateBetween(UUID tenantId, UUID employeeId,
			WorkStatus status, LocalDate from, LocalDate to, Pageable pageable);
	List<WorkDay> findByTenantIdAndStatusAndWorkDateBetween(UUID tenantId, WorkStatus status,
			LocalDate from, LocalDate to);
	List<WorkDay> findByTenantIdAndEmployeeIdAndStatusAndWorkDateBetween(UUID tenantId, UUID employeeId,
			WorkStatus status, LocalDate from, LocalDate to);
}
