package com.domanski.smsmodular.entitlements.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.entitlements.entity.TenantAddon;

public interface TenantAddonRepository extends JpaRepository<TenantAddon, UUID> {
	List<TenantAddon> findByTenantId(UUID tenantId);
	Optional<TenantAddon> findByTenantIdAndModuleKey(UUID tenantId, String moduleKey);
}
