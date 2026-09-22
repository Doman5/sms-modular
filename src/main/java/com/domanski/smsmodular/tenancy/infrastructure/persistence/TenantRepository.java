package com.domanski.smsmodular.tenancy.infrastructure.persistence;

import com.domanski.smsmodular.tenancy.domain.Tenant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class TenantRepository {

	private final TenantJpaRepository jpaRepository;
	private final ConcurrentMap<UUID, Tenant> testTenants;

	@Autowired
	public TenantRepository(TenantJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
		this.testTenants = null;
	}

	public TenantRepository() {
		this.jpaRepository = null;
		this.testTenants = new ConcurrentHashMap<>();
	}

	public Tenant save(Tenant tenant) {
		if (testTenants != null) {
			testTenants.put(tenant.id().value(), tenant);
			return tenant;
		}
		TenantEntity entity = jpaRepository.findById(tenant.id().value())
			.map(existing -> {
				existing.copyMutableState(tenant);
				return existing;
			})
			.orElseGet(() -> TenantEntity.from(tenant));
		return jpaRepository.save(entity).toDomain();
	}

	public Optional<Tenant> findById(UUID tenantId) {
		if (testTenants != null) {
			return Optional.ofNullable(testTenants.get(tenantId));
		}
		return jpaRepository.findById(tenantId).map(TenantEntity::toDomain);
	}

	public Optional<Tenant> findBySlug(String slug) {
		if (testTenants != null) {
			return testTenants.values().stream()
				.filter(tenant -> tenant.slug().equals(slug))
				.findFirst();
		}
		return jpaRepository.findBySlug(slug).map(TenantEntity::toDomain);
	}

	public Page<Tenant> findAll(Pageable pageable) {
		if (testTenants == null) {
			return jpaRepository.findAll(pageable).map(TenantEntity::toDomain);
		}
		List<Tenant> sorted = new ArrayList<>(testTenants.values());
		sorted.sort(Comparator.comparing(Tenant::createdAt));
		int from = Math.min((int) pageable.getOffset(), sorted.size());
		int to = Math.min(from + pageable.getPageSize(), sorted.size());
		return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
	}
}
