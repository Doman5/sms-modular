package com.domanski.smsmodular.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
	info = @Info(
		title = "SMS Modular API",
		version = "v1",
		description = "Neutral API foundation for a modular multi-tenant SaaS platform."
	)
)
public class OpenApiConfiguration {
}
