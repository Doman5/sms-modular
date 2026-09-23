package com.domanski.smsmodular.entitlements.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.entitlements.entity.TenantSubscription;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {
	Optional<TenantSubscription> findByTenantId(UUID tenantId);
}
