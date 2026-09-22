package com.domanski.smsmodular.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?")
		String slug,
		@NotBlank @Size(max = 160) String name,
		@NotBlank @Size(max = 64) String timeZone,
		@NotBlank @Size(max = 35) String locale) {
}
