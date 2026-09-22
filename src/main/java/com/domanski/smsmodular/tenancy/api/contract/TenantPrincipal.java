package com.domanski.smsmodular.tenancy.api.contract;

import java.util.Objects;





public record TenantPrincipal(String subject, TenantId tenantId) {

	public TenantPrincipal {
		if (subject == null || subject.isBlank()) {
			throw new IllegalArgumentException("Principal subject is required");
		}
		Objects.requireNonNull(tenantId, "Tenant ID is required");
	}
}
