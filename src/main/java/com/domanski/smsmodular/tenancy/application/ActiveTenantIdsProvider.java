package com.domanski.smsmodular.tenancy.application;

import com.domanski.smsmodular.tenancy.api.contract.ActiveTenantIdsPort;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.domain.TenantStatus;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Profile("!test")
public class ActiveTenantIdsProvider implements ActiveTenantIdsPort {

	private final TenantRepository tenantStore;

	public ActiveTenantIdsProvider(TenantRepository tenantStore) {
		this.tenantStore = tenantStore;
	}

	@Override
	@Transactional(readOnly = true)
	public List<TenantId> activeTenantIds() {
		List<TenantId> result = new java.util.ArrayList<>();
		int page = 0;
		org.springframework.data.domain.Page<Tenant> tenants;
		do {
			tenants = tenantStore.findAll(PageRequest.of(page++, 500, Sort.by("id")));
			tenants.stream()
				.filter(tenant -> tenant.status() == TenantStatus.ACTIVE)
				.map(Tenant::id)
				.forEach(result::add);
		} while (tenants.hasNext());
		return List.copyOf(result);
	}
}
