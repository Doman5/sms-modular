package com.domanski.smsmodular.common.api;


public record ApiFieldError(
	String field,
	String message
) {
}
