package com.domanski.smsmodular.entitlements.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.entitlements.entity.PlanVersion;

public interface PlanVersionRepository extends JpaRepository<PlanVersion, UUID> {
}
