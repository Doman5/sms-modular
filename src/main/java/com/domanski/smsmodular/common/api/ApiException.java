package com.domanski.smsmodular.common.api;

import org.springframework.http.HttpStatusCode;

public class ApiException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;

	public ApiException(HttpStatusCode status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
