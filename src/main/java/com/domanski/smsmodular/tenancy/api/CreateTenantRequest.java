package com.domanski.smsmodular.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
	@NotBlank @Size(max = 64)
	@Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")
	String slug,
	@NotBlank @Size(max = 255)
	String name,
	@NotBlank @Size(max = 64)
	String timezone,
	@NotBlank @Size(max = 32)
	String locale
) {
}
