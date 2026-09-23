package com.domanski.smsmodular.time.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.time.entity.WorkInterval;
import com.domanski.smsmodular.time.entity.WorkStatus;

public interface WorkIntervalRepository extends JpaRepository<WorkInterval, UUID> {
	List<WorkInterval> findByTenantIdAndWorkDayIdAndStatusOrderByStartMinuteAsc(UUID tenantId, UUID workDayId,
			WorkStatus status);
}
