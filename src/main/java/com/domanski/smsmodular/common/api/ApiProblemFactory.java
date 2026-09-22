package com.domanski.smsmodular.common.api;

import com.domanski.smsmodular.common.context.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;


@Component
public class ApiProblemFactory {

	public ProblemDetail create(
		HttpStatus status,
		ApiProblemCode code,
		String message,
		HttpServletRequest request,
		List<ApiFieldError> fieldErrors
	) {
		String correlationId = CorrelationContext.currentOrGenerate();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
		problem.setType(URI.create("urn:sms-modular:problem:" + code.name().toLowerCase()));
		problem.setTitle(status.getReasonPhrase());
		if (request != null) {
			problem.setInstance(URI.create(request.getRequestURI()));
		}
		problem.setProperty("code", code.name());
		problem.setProperty("message", message);
		problem.setProperty("correlationId", correlationId);
		if (fieldErrors != null && !fieldErrors.isEmpty()) {
			problem.setProperty("fieldErrors", List.copyOf(fieldErrors));
		}
		return problem;
	}

	public ProblemDetail create(
		HttpStatus status,
		ApiProblemCode code,
		String message,
		HttpServletRequest request
	) {
		return create(status, code, message, request, List.of());
	}
}
