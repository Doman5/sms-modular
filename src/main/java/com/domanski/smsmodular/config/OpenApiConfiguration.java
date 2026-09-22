package com.domanski.smsmodular.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfiguration {

	@Bean
	OpenAPI smsModularOpenApi() {
		return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
				new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.info(new Info()
				.title("SMS Modular API")
				.version("v1")
				.description("API for SMS Modular"));
	}
}
