package com.domanski.smsmodular.tenancy.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.domanski.smsmodular.common.api.ApiProblemFactory;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class TenantContextFilterTest {

	@AfterEach
	void cleanup() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void readsOnlyVerifiedPrincipalAndAlwaysClearsStaleThreadLocalValue() throws Exception {
		TenantId stale = TenantId.of(UUID.randomUUID());
		TenantId current = TenantId.of(UUID.randomUUID());
		TenantContext.open(stale);
		SecurityContextHolder.getContext().setAuthentication(authentication(current));

		MockHttpServletRequest request = request();
		MockHttpServletResponse response = new MockHttpServletResponse();
		new TenantContextFilter(new ApiProblemFactory(), JsonMapper.builder().build())
			.doFilter(request, response, (servletRequest, servletResponse) -> {
				assertThat(TenantContext.require()).isEqualTo(current);
			});

		assertThat(TenantContext.current()).isEmpty();
	}

	@Test
	void missingTenantClaimReturnsSafeProblemDetails() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken("platform", "n/a",
				java.util.List.of(new SimpleGrantedAuthority("TENANT_SETTINGS_EDIT"))));
		MockHttpServletResponse response = new MockHttpServletResponse();

		new TenantContextFilter(new ApiProblemFactory(), JsonMapper.builder().build())
			.doFilter(request(), response, (servletRequest, servletResponse) -> {
				throw new AssertionError("invalid principal must not reach MVC");
			});

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.getContentAsString()).contains("INVALID_TENANT_CONTEXT");
		assertThat(TenantContext.current()).isEmpty();
	}

	@Test
	void platformRouteNeverReceivesOrLeavesTenantContext() throws Exception {
		TenantContext.open(TenantId.of(UUID.randomUUID()));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/v1/tenants");
		MockHttpServletResponse response = new MockHttpServletResponse();

		new TenantContextFilter(new ApiProblemFactory(), JsonMapper.builder().build())
			.doFilter(request, response, (servletRequest, servletResponse) ->
				assertThat(TenantContext.current()).isEmpty());

		assertThat(TenantContext.current()).isEmpty();
	}

	private MockHttpServletRequest request() {
		return new MockHttpServletRequest("GET", "/api/v1/tenant");
	}

	private UsernamePasswordAuthenticationToken authentication(TenantId tenantId) {
		return new UsernamePasswordAuthenticationToken(
			new TenantPrincipal("subject", tenantId),
			"n/a",
			java.util.List.of(new SimpleGrantedAuthority("TENANT_SETTINGS_EDIT"))
		);
	}
}
