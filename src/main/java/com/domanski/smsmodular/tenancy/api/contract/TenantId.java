package com.domanski.smsmodular.tenancy.api.contract;

import java.util.Objects;
import java.util.UUID;


public record TenantId(UUID value) {

	public TenantId {
		Objects.requireNonNull(value, "Tenant ID is required");
	}

	public static TenantId of(UUID value) {
		return new TenantId(value);
	}

	public static TenantId parse(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Tenant ID is required");
		}
		try {
			return new TenantId(UUID.fromString(value));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Tenant ID must be a UUID", exception);
		}
	}

	
	public UUID uuid() {
		return value;
	}
}
