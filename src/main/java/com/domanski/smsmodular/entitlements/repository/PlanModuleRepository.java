package com.domanski.smsmodular.entitlements.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.entitlements.entity.PlanModule;

public interface PlanModuleRepository extends JpaRepository<PlanModule, UUID> {
	List<PlanModule> findByPlanVersionId(UUID planVersionId);
}
