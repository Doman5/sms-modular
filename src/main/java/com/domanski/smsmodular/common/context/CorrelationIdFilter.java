package com.domanski.smsmodular.common.context;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Correlation-Id";
	public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";
	private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String suppliedId = request.getHeader(HEADER);
		String correlationId = suppliedId != null && VALID_ID.matcher(suppliedId).matches()
				? suppliedId
				: UUID.randomUUID().toString();
		request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
		response.setHeader(HEADER, correlationId);
		filterChain.doFilter(request, response);
	}
}
