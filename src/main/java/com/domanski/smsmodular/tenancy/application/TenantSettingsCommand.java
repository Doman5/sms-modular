package com.domanski.smsmodular.tenancy.application;


public record TenantSettingsCommand(String name, String timezone, String locale) {

	public boolean hasAnyValue() {
		return name != null || timezone != null || locale != null;
	}
}
