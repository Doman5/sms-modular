package com.domanski.smsmodular.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String requestedId = request.getHeader(CorrelationContext.HEADER_NAME);
		String correlationId = CorrelationContext.isSafe(requestedId)
			? requestedId
			: UUID.randomUUID().toString();

		CorrelationContext.set(correlationId);
		MDC.put(CorrelationContext.MDC_KEY, correlationId);
		response.setHeader(CorrelationContext.HEADER_NAME, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(CorrelationContext.MDC_KEY);
			CorrelationContext.clear();
		}
	}
}
