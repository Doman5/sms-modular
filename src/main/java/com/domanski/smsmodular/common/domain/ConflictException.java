package com.domanski.smsmodular.common.domain;

import com.domanski.smsmodular.common.api.ApiProblemCode;


public class ConflictException extends DomainException {

	public ConflictException(String message) {
		super(ApiProblemCode.CONFLICT, message);
	}
}
