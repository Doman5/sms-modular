package com.domanski.smsmodular.common.api;

import com.domanski.smsmodular.common.domain.ConflictException;
import com.domanski.smsmodular.common.domain.DomainException;
import com.domanski.smsmodular.common.context.TenantContextRequiredException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;


@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ApiProblemFactory problemFactory;

	public GlobalExceptionHandler(ApiProblemFactory problemFactory) {
		this.problemFactory = problemFactory;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public org.springframework.http.ProblemDetail handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		return validationProblem(exception, request);
	}

	@ExceptionHandler(BindException.class)
	public org.springframework.http.ProblemDetail handleBindException(
		BindException exception,
		HttpServletRequest request
	) {
		return validationProblem(exception, request);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public org.springframework.http.ProblemDetail handleMethodValidation(
		HandlerMethodValidationException exception,
		HttpServletRequest request
	) {
		List<ApiFieldError> fieldErrors = new ArrayList<>();
		exception.getParameterValidationResults().forEach(result -> {
			String field = result.getMethodParameter().getParameterName();
			if (result instanceof ParameterErrors parameterErrors) {
				fieldErrors.addAll(mapErrors(parameterErrors.getFieldErrors()));
				fieldErrors.addAll(mapGlobalErrors(parameterErrors.getGlobalErrors(), field));
			} else {
				String safeField = field == null ? "request" : field;
				result.getResolvableErrors().forEach(error -> fieldErrors.add(
					new ApiFieldError(safeField, safeMessage(error))
				));
			}
		});
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.VALIDATION_ERROR,
			"The request contains invalid values.",
			request,
			fieldErrors
		);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public org.springframework.http.ProblemDetail handleConstraintViolation(
		ConstraintViolationException exception,
		HttpServletRequest request
	) {
		List<ApiFieldError> fieldErrors = exception.getConstraintViolations().stream()
			.map(violation -> new ApiFieldError(
				violation.getPropertyPath().toString(),
				violation.getMessage() == null ? "Invalid value" : violation.getMessage()
			))
			.toList();
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.VALIDATION_ERROR,
			"The request contains invalid values.",
			request,
			fieldErrors
		);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public org.springframework.http.ProblemDetail handleUnreadableMessage(HttpServletRequest request) {
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.MALFORMED_REQUEST,
			"The request body is malformed.",
			request
		);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public org.springframework.http.ProblemDetail handleTypeMismatch(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		String field = exception.getName() == null ? "request" : exception.getName();
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.VALIDATION_ERROR,
			"The request contains invalid values.",
			request,
			List.of(new ApiFieldError(field, "Invalid value"))
		);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public org.springframework.http.ProblemDetail handleMissingParameter(
		MissingServletRequestParameterException exception,
		HttpServletRequest request
	) {
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.VALIDATION_ERROR,
			"The request contains invalid values.",
			request,
			List.of(new ApiFieldError(exception.getParameterName(), "A value is required"))
		);
	}

	@ExceptionHandler(ConflictException.class)
	public org.springframework.http.ProblemDetail handleConflict(
		ConflictException exception,
		HttpServletRequest request
	) {
		return problemFactory.create(
			HttpStatus.CONFLICT,
			ApiProblemCode.CONFLICT,
			safeDomainMessage(exception.getMessage()),
			request
		);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public org.springframework.http.ProblemDetail handleDataConflict(HttpServletRequest request) {
		return problemFactory.create(
			HttpStatus.CONFLICT,
			ApiProblemCode.CONFLICT,
			"The requested operation conflicts with existing data.",
			request
		);
	}

	@ExceptionHandler(DomainException.class)
	public org.springframework.http.ProblemDetail handleDomain(
		DomainException exception,
		HttpServletRequest request
	) {
		return problemFactory.create(
			HttpStatus.UNPROCESSABLE_CONTENT,
			exception.problemCode(),
			safeDomainMessage(exception.getMessage()),
			request
		);
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public org.springframework.http.ProblemDetail handleNotFound(HttpServletRequest request) {
		return problemFactory.create(
			HttpStatus.NOT_FOUND,
			ApiProblemCode.NOT_FOUND,
			"The requested resource was not found.",
			request
		);
	}

	@ExceptionHandler(AuthenticationException.class)
	public org.springframework.http.ProblemDetail handleAuthentication(
		HttpServletRequest request
	) {
		return problemFactory.create(
			HttpStatus.UNAUTHORIZED,
			ApiProblemCode.UNAUTHORIZED,
			"Authentication is required.",
			request
		);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public org.springframework.http.ProblemDetail handleAccessDenied(HttpServletRequest request) {
		return problemFactory.create(
			HttpStatus.FORBIDDEN,
			ApiProblemCode.FORBIDDEN,
			"You are not allowed to perform this operation.",
			request
		);
	}

	@ExceptionHandler(TenantContextRequiredException.class)
	public org.springframework.http.ProblemDetail handleTenantContextRequired(
		TenantContextRequiredException exception,
		HttpServletRequest request
	) {
		return problemFactory.create(
			HttpStatus.FORBIDDEN,
			ApiProblemCode.TENANT_CONTEXT_REQUIRED,
			"A verified tenant context is required for this operation.",
			request
		);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public org.springframework.http.ProblemDetail handleIllegalArgument(HttpServletRequest request) {
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.VALIDATION_ERROR,
			"The request contains invalid values.",
			request
		);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public org.springframework.http.ProblemDetail handleResponseStatus(
		ResponseStatusException exception,
		HttpServletRequest request
	) {
		HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
		if (status == null || status.is5xxServerError()) {
			return problemFactory.create(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ApiProblemCode.INTERNAL_ERROR,
				"An unexpected error occurred.",
				request
			);
		}
		ApiProblemCode code = status == HttpStatus.NOT_FOUND
			? ApiProblemCode.NOT_FOUND
			: ApiProblemCode.MALFORMED_REQUEST;
		return problemFactory.create(status, code, "The request could not be completed.", request);
	}

	@ExceptionHandler(Exception.class)
	public org.springframework.http.ProblemDetail handleUnexpectedException(
		Exception exception,
		HttpServletRequest request
	) {
		LOGGER.error(
			"Unhandled API exception type={} correlationId={} path={}",
			exception.getClass().getName(),
			com.domanski.smsmodular.common.context.CorrelationContext.currentOrGenerate(),
			request.getRequestURI()
		);
		return problemFactory.create(
			HttpStatus.INTERNAL_SERVER_ERROR,
			ApiProblemCode.INTERNAL_ERROR,
			"An unexpected error occurred.",
			request
		);
	}

	private org.springframework.http.ProblemDetail validationProblem(
		BindException exception,
		HttpServletRequest request
	) {
		List<ApiFieldError> fieldErrors = new ArrayList<>(mapErrors(exception.getFieldErrors()));
		fieldErrors.addAll(mapGlobalErrors(exception.getGlobalErrors(), "request"));
		return problemFactory.create(
			HttpStatus.BAD_REQUEST,
			ApiProblemCode.VALIDATION_ERROR,
			"The request contains invalid values.",
			request,
			fieldErrors
		);
	}

	private List<ApiFieldError> mapErrors(List<? extends FieldError> errors) {
		return errors.stream()
			.map(error -> new ApiFieldError(error.getField(), safeMessage(error)))
			.toList();
	}

	private List<ApiFieldError> mapGlobalErrors(List<? extends ObjectError> errors, String defaultField) {
		return errors.stream()
			.map(error -> new ApiFieldError(defaultField, safeMessage(error)))
			.toList();
	}

	private String safeMessage(org.springframework.context.MessageSourceResolvable error) {
		String message = error.getDefaultMessage();
		return message == null || message.isBlank() ? "Invalid value" : message;
	}

	private String safeDomainMessage(String message) {
		return message == null || message.isBlank() ? "The operation violates a business rule." : message;
	}
}
