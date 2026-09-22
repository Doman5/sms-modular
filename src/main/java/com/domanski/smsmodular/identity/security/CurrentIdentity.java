package com.domanski.smsmodular.identity.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentIdentity {

	public CurrentPrincipal principal() {
		return (CurrentPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}

	public java.util.UUID tenantId() {
		return principal().tenantId();
	}
}
