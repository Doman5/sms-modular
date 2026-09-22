package com.domanski.smsmodular.common.api;

import java.util.List;









public record ApiProblem(
	int status,
	ApiProblemCode code,
	String message,
	String correlationId,
	List<ApiFieldError> fieldErrors
) {
	public ApiProblem {
		if (status < 400 || status > 599) {
			throw new IllegalArgumentException("Problem status must be an HTTP error status");
		}
		if (code == null) {
			throw new IllegalArgumentException("Problem code is required");
		}
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("Problem message is required");
		}
		if (correlationId == null || correlationId.isBlank()) {
			throw new IllegalArgumentException("Problem correlation ID is required");
		}
		fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}
}
