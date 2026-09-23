package com.domanski.smsmodular.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.domanski.smsmodular.common.context.CorrelationIdFilter;
import com.domanski.smsmodular.identity.security.IdentityJwtConverter;
import com.domanski.smsmodular.identity.security.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, IdentityJwtConverter converter, JsonMapper json) throws Exception {
		return http.cors(Customizer.withDefaults())
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
								"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
								"/api/v1/auth/login", "/api/platform/v1/auth/login",
								"/api/integrations/v1/sms/sms-gate/inbound").permitAll()
						.requestMatchers("/api/v1/auth/change-password", "/api/v1/me/context").hasAuthority("TENANT_USER")
						.requestMatchers("/api/platform/v1/auth/change-password", "/api/platform/v1/me/context")
								.hasAuthority("PLATFORM_USER")
						.requestMatchers("/api/platform/v1/**").hasAuthority("PLATFORM_ACCESS")
						.requestMatchers("/api/v1/**").hasAuthority("TENANT_ACCESS")
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) ->
								writeProblem(json, request, response, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED"))
						.accessDeniedHandler((request, response, exception) ->
								writeProblem(json, request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED")))
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	SecretKey jwtSecret(@Value("${app.security.jwt-secret-base64:}") String encoded) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encoded);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("AUTH_JWT_SECRET_BASE64 must be valid Base64", exception);
		}
		if (bytes.length < 32) {
			throw new IllegalStateException("AUTH_JWT_SECRET_BASE64 must contain at least 32 random bytes");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecret) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecret) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecret).macAlgorithm(MacAlgorithm.HS256).build();
		OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(TokenService.AUDIENCE)
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(TokenService.ISSUER), audience));
		return decoder;
	}

	private void writeProblem(JsonMapper json, HttpServletRequest request, HttpServletResponse response,
			HttpStatus status, String code) throws java.io.IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, status == HttpStatus.UNAUTHORIZED
				? "Authentication is required" : "Access is denied");
		problem.setProperty("code", code);
		Object correlation = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
		problem.setProperty("correlationId", correlation == null ? "unavailable" : correlation);
		json.writeValue(response.getWriter(), problem);
	}
}
