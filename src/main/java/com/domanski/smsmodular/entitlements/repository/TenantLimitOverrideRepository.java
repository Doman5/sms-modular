package com.domanski.smsmodular.entitlements.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.entitlements.entity.TenantLimitOverride;

public interface TenantLimitOverrideRepository extends JpaRepository<TenantLimitOverride, UUID> {
	Optional<TenantLimitOverride> findByTenantIdAndMetricCode(UUID tenantId, String metricCode);
}
