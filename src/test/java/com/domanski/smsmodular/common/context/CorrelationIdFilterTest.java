package com.domanski.smsmodular.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

	private final CorrelationIdFilter filter = new CorrelationIdFilter();

	@AfterEach
	void clearThreadContext() {
		CorrelationContext.clear();
		MDC.clear();
	}

	@Test
	void acceptsSafeClientIdAndClearsContextAfterRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationContext.HEADER_NAME, "client-request_42");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
			assertThat(CorrelationContext.current()).isEqualTo("client-request_42");
			assertThat(MDC.get(CorrelationContext.MDC_KEY)).isEqualTo("client-request_42");
		});

		assertThat(response.getHeader(CorrelationContext.HEADER_NAME)).isEqualTo("client-request_42");
		assertThat(CorrelationContext.current()).isNull();
		assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
	}

	@Test
	void replacesUnsafeHeaderWithGeneratedUuid() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CorrelationContext.HEADER_NAME, "bad\r\nX-Injected: true");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
			assertThat(UUID.fromString(CorrelationContext.requireCurrent())).isNotNull();
		});

		assertThat(UUID.fromString(response.getHeader(CorrelationContext.HEADER_NAME))).isNotNull();
		assertThat(response.getHeader(CorrelationContext.HEADER_NAME)).doesNotContain("Injected");
	}

	@Test
	void clearsContextWhenDownstreamThrows() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain failingChain = (servletRequest, servletResponse) -> {
			throw new IllegalStateException("expected test failure");
		};

		assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("expected test failure");
		assertThat(CorrelationContext.current()).isNull();
		assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
	}
}
