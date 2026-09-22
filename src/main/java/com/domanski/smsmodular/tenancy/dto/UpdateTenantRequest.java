package com.domanski.smsmodular.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
		@NotBlank @Size(max = 160) String name,
		@NotBlank @Size(max = 64) String timeZone,
		@NotBlank @Size(max = 35) String locale) {
}
