package com.domanski.smsmodular.absence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.domanski.smsmodular.absence.entity.AbsenceDay;
import com.domanski.smsmodular.absence.entity.AbsenceStatus;

public interface AbsenceDayRepository extends JpaRepository<AbsenceDay, UUID> {
	Optional<AbsenceDay> findByTenantIdAndId(UUID tenantId, UUID id);
	@Query("select a.employeeId from AbsenceDay a where a.tenantId = :tenantId and a.id = :id")
	Optional<UUID> employeeId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
	boolean existsByTenantIdAndEmployeeIdAndAbsenceDateAndStatus(UUID tenantId, UUID employeeId,
			LocalDate date, AbsenceStatus status);
	Page<AbsenceDay> findByTenantIdAndStatusAndAbsenceDateBetween(UUID tenantId, AbsenceStatus status,
			LocalDate from, LocalDate to, Pageable pageable);
	Page<AbsenceDay> findByTenantIdAndEmployeeIdAndStatusAndAbsenceDateBetween(UUID tenantId, UUID employeeId,
			AbsenceStatus status, LocalDate from, LocalDate to, Pageable pageable);
	List<AbsenceDay> findByTenantIdAndEmployeeIdAndStatusAndAbsenceDateBetween(UUID tenantId, UUID employeeId,
			AbsenceStatus status, LocalDate from, LocalDate to);
	List<AbsenceDay> findByTenantIdAndStatusAndAbsenceDateBetween(UUID tenantId, AbsenceStatus status,
			LocalDate from, LocalDate to);
}
