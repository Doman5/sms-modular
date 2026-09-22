package com.domanski.smsmodular.common.api;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import com.domanski.smsmodular.common.context.CorrelationIdFilter;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
		ProblemDetail problem = createProblem(exception.getStatus(), exception.getCode(), exception.getMessage(), request);
		problem.setTitle("Request failed");
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleInvalidArguments(MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiFieldError(error.getField(), Objects.toString(error.getDefaultMessage(), "Invalid value")))
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
				.toList();
		ProblemDetail problem = createProblem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
		problem.setProperty("fieldErrors", fieldErrors);
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
		List<ApiFieldError> fieldErrors = exception.getConstraintViolations().stream()
				.map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
				.sorted(Comparator.comparing(ApiFieldError::field).thenComparing(ApiFieldError::message))
				.toList();
		ProblemDetail problem = createProblem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
		problem.setProperty("fieldErrors", fieldErrors);
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
		return createProblem(HttpStatus.BAD_REQUEST, "REQUEST_BODY_INVALID", "Request body is invalid", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
		return createProblem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied", request);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail handleAuthenticationFailure(AuthenticationException exception, HttpServletRequest request) {
		return createProblem(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication is required", request);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception exception, HttpServletRequest request) {
		if (exception instanceof ErrorResponse errorResponse) {
			HttpStatusCode status = errorResponse.getStatusCode();
			ProblemDetail problem = createProblem(status, "HTTP_" + status.value(), "Request failed", request);
			String title = errorResponse.getBody().getTitle();
			if (title != null && !title.isBlank()) {
				problem.setTitle(title);
			}
			return problem;
		}
		String correlationId = correlationId(request);
		logger.error("Unhandled request failure correlationId={}", correlationId, exception);
		return createProblem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
	}

	private ProblemDetail createProblem(HttpStatus status, String code, String message, HttpServletRequest request) {
		return createProblem((HttpStatusCode) status, code, message, request);
	}

	private ProblemDetail createProblem(HttpStatusCode status, String code, String message, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
		problem.setType(URI.create("about:blank"));
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setTitle(status.is4xxClientError() ? "Request failed" : "Server error");
		problem.setProperty("code", code);
		problem.setProperty("message", message);
		problem.setProperty("correlationId", correlationId(request));
		return problem;
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
		return value instanceof String correlationId ? correlationId : "unavailable";
	}
}
