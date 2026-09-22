package com.domanski.smsmodular.integrationruntime.application;


public class IntegrationRuntimeFailure extends RuntimeException {

	private final String errorCode;

	public IntegrationRuntimeFailure(String errorCode, String safeMessage) {
		super(safeMessage);
		if (errorCode == null || !errorCode.matches("[A-Z][A-Z0-9_.-]{1,63}")) {
			throw new IllegalArgumentException("Integration error code has an invalid format");
		}
		this.errorCode = errorCode;
	}

	public String errorCode() {
		return errorCode;
	}
}
