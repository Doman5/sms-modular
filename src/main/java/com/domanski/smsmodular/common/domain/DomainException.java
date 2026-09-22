package com.domanski.smsmodular.common.domain;

import com.domanski.smsmodular.common.api.ApiProblemCode;


public class DomainException extends RuntimeException {

	private final ApiProblemCode problemCode;

	public DomainException(String message) {
		this(ApiProblemCode.DOMAIN_ERROR, message);
	}

	public DomainException(ApiProblemCode problemCode, String message) {
		super(requireMessage(message));
		if (problemCode == null) {
			throw new IllegalArgumentException("Problem code is required");
		}
		this.problemCode = problemCode;
	}

	public ApiProblemCode problemCode() {
		return problemCode;
	}

	private static String requireMessage(String message) {
		if (message == null || message.isBlank()) {
			throw new IllegalArgumentException("Domain error message is required");
		}
		return message;
	}
}
