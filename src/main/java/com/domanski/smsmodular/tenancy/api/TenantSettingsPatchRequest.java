package com.domanski.smsmodular.tenancy.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record TenantSettingsPatchRequest(
	@Size(max = 255) String name,
	@Size(max = 64) String timezone,
	@Size(max = 32) String locale
) {

	@AssertTrue(message = "At least one tenant setting is required.")
	public boolean hasAtLeastOneSetting() {
		return name != null || timezone != null || locale != null;
	}
}
