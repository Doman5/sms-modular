package com.domanski.smsmodular.tenancy.domain;

import com.domanski.smsmodular.common.api.ApiProblemCode;
import com.domanski.smsmodular.common.domain.DomainException;


public final class TenantStatusTransitionException extends DomainException {

	public TenantStatusTransitionException(String message, ApiProblemCode problemCode) {
		super(problemCode, message);
	}
}
