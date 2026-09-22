package com.domanski.smsmodular.tenancy.api.contract;

import java.util.List;





public interface ActiveTenantIdsPort {

	List<TenantId> activeTenantIds();
}
