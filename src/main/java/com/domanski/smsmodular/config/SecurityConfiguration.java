package com.domanski.smsmodular.config;

import com.domanski.smsmodular.common.api.ApiProblemCode;
import com.domanski.smsmodular.common.api.ApiProblemFactory;
import com.domanski.smsmodular.common.context.CorrelationContext;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;






@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		AuthenticationEntryPoint authenticationEntryPoint,
		AccessDeniedHandler accessDeniedHandler
	) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptionHandling -> exceptionHandling
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
			)
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					"/api/system/**",
					"/v3/api-docs/**",
					"/swagger-ui/**",
					"/swagger-ui.html",
					"/actuator/health"
				).permitAll()
				.requestMatchers("/api/platform/v1/**").hasAnyAuthority(
					"PLATFORM_OPERATOR",
					"PLATFORM_AUDIT_READ",
					"PLATFORM_INTEGRATION_READ",
					"PLATFORM_INTEGRATION_RETRY",
					"PLATFORM_TENANT_CREATE",
					"PLATFORM_TENANT_READ",
					"PLATFORM_TENANT_UPDATE",
					"PLATFORM_TENANT_SUSPEND",
					"PLATFORM_TENANT_ACTIVATE",
					"PLATFORM_TENANT_CLOSE"
				)
				.requestMatchers("/api/v1/**", "/api/integrations/v1/**").authenticated()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth2 -> oauth2
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
			);
		return http.build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new JwtPermissionsConverter());
		return converter;
	}

	
	private static final class JwtPermissionsConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

		@Override
		public Collection<GrantedAuthority> convert(Jwt jwt) {
			Set<String> values = new LinkedHashSet<>();
			addClaim(values, jwt.getClaims().get("permissions"));
			addClaim(values, jwt.getClaims().get("authorities"));
			if (values.isEmpty()) {
				Object scopeClaim = jwt.getClaims().get("scope");
				if (scopeClaim instanceof String scope) {
					for (String value : scope.split("\\s+")) {
						if (!value.isBlank()) {
							values.add("SCOPE_" + value);
						}
					}
				}
			}
			return values.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
		}

		private static void addClaim(Set<String> values, Object claim) {
			if (claim instanceof Collection<?> collection) {
				collection.stream().filter(String.class::isInstance).map(String.class::cast)
					.filter(value -> !value.isBlank()).forEach(values::add);
			} else if (claim instanceof String value && !value.isBlank()) {
				for (String item : value.split("\\s+")) {
					if (!item.isBlank()) {
						values.add(item);
					}
				}
			}
		}
	}

	@Bean
	public JwtDecoder jwtDecoder(
		@Value("${app.security.jwt-issuer-uri:}") String issuerUri
	) {
		if (issuerUri != null && !issuerUri.isBlank()) {
			return JwtDecoders.fromIssuerLocation(issuerUri);
		}
		return token -> {
			throw new JwtValidationException(
				"JWT issuer is not configured",
				java.util.List.of(new OAuth2Error("invalid_token"))
			);
		};
	}

	@Bean
	public AuthenticationEntryPoint authenticationEntryPoint(
		JsonMapper objectMapper,
		ApiProblemFactory problemFactory
	) {
		return (request, response, exception) -> writeProblem(
			request,
			response,
			objectMapper,
			problemFactory,
			HttpStatus.UNAUTHORIZED,
			ApiProblemCode.UNAUTHORIZED,
			"Authentication is required."
		);
	}

	@Bean
	public AccessDeniedHandler accessDeniedHandler(
		JsonMapper objectMapper,
		ApiProblemFactory problemFactory
	) {
		return (request, response, exception) -> writeProblem(
			request,
			response,
			objectMapper,
			problemFactory,
			HttpStatus.FORBIDDEN,
			ApiProblemCode.FORBIDDEN,
			"You are not allowed to perform this operation."
		);
	}

	private void writeProblem(
		HttpServletRequest request,
		HttpServletResponse response,
		JsonMapper objectMapper,
		ApiProblemFactory problemFactory,
		HttpStatus status,
		ApiProblemCode code,
		String message
	) throws IOException {
		String correlationId = CorrelationContext.currentOrGenerate();
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader(CorrelationContext.HEADER_NAME, correlationId);
		ProblemDetail problem = problemFactory.create(status, code, message, request);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}
}
