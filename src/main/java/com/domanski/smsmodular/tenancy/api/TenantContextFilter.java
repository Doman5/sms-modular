package com.domanski.smsmodular.tenancy.api;

import com.domanski.smsmodular.common.api.ApiProblemCode;
import com.domanski.smsmodular.common.api.ApiProblemFactory;
import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;






@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public final class TenantContextFilter extends OncePerRequestFilter {

	private static final String TENANT_API_PREFIX = "/api/v1/";

	private final ApiProblemFactory problemFactory;
	private final JsonMapper objectMapper;

	public TenantContextFilter(ApiProblemFactory problemFactory, JsonMapper objectMapper) {
		this.problemFactory = problemFactory;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			
			
			TenantContext.clear();
			String path = request.getRequestURI();
			if (path == null || !path.startsWith(TENANT_API_PREFIX)) {
				
				
				filterChain.doFilter(request, response);
				return;
			}
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null || !authentication.isAuthenticated()
				|| authentication.getPrincipal() == null
				|| "anonymousUser".equals(authentication.getPrincipal())) {
				
				filterChain.doFilter(request, response);
				return;
			}

			TenantId tenantId;
			try {
				tenantId = extractTenantId(authentication);
			} catch (IllegalArgumentException exception) {
				writeProblem(request, response, HttpStatus.FORBIDDEN, ApiProblemCode.INVALID_TENANT_CONTEXT,
					"The authenticated principal does not contain a valid tenant context.");
				return;
			}

			try (TenantContext.Scope ignored = TenantContext.open(tenantId)) {
				filterChain.doFilter(request, response);
			}
		} finally {
			
			TenantContext.clear();
		}
	}

	private TenantId extractTenantId(Authentication authentication) {
		Object principal = authentication.getPrincipal();
		if (principal instanceof TenantPrincipal tenantPrincipal) {
			return tenantPrincipal.tenantId();
		}
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
			Object claim = jwtAuthentication.getToken().getClaims().get("tenantId");
			if (claim == null) {
				claim = jwtAuthentication.getToken().getClaims().get("tenant_id");
			}
			if (claim instanceof UUID uuid) {
				return TenantId.of(uuid);
			}
			if (claim instanceof String value && !value.isBlank()) {
				return TenantId.parse(value);
			}
		}
		throw new IllegalArgumentException("Authenticated principal has no tenant claim");
	}

	private void writeProblem(
		HttpServletRequest request,
		HttpServletResponse response,
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
