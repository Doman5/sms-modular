package com.domanski.smsmodular.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final CorsProperties corsProperties;

	public WebConfig(CorsProperties corsProperties) {
		this.corsProperties = corsProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
			.allowedOrigins(allowedOrigins())
			.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("*")
			.allowCredentials(false);
	}

	private String[] allowedOrigins() {
		String configuredOrigins = corsProperties.allowedOrigins();
		if (configuredOrigins == null || configuredOrigins.isBlank()) {
			return new String[] { "http://localhost:4200" };
		}

		List<String> origins = Arrays.stream(configuredOrigins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isBlank())
			.toList();
		return origins.isEmpty() ? new String[] { "http://localhost:4200" } : origins.toArray(String[]::new);
	}

}
