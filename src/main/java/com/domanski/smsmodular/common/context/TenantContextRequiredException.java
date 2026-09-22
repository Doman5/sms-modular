package com.domanski.smsmodular.common.context;


public class TenantContextRequiredException extends IllegalStateException {

	public TenantContextRequiredException() {
		super("A verified tenant context is required");
	}
}
