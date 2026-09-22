package com.domanski.smsmodular.tenancy.application;

import com.domanski.smsmodular.tenancy.api.contract.ActiveTenantIdsPort;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class EmptyActiveTenantIdsProvider implements ActiveTenantIdsPort {

	@Override
	public List<TenantId> activeTenantIds() {
		return List.of();
	}
}
