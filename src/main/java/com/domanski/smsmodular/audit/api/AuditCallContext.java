package com.domanski.smsmodular.audit.api;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.domanski.smsmodular.common.context.CorrelationIdFilter;

public record AuditCallContext(AuditActorType actorType, UUID actorId, String correlationId) {

	public static AuditCallContext tenantUser(UUID actorId, HttpServletRequest request) {
		return new AuditCallContext(AuditActorType.TENANT_USER, actorId, correlationId(request));
	}

	public static AuditCallContext platformUser(UUID actorId, HttpServletRequest request) {
		return new AuditCallContext(AuditActorType.PLATFORM_USER, actorId, correlationId(request));
	}

	public static AuditCallContext system(String correlationId) {
		return new AuditCallContext(AuditActorType.SYSTEM, null, correlationId);
	}

	public static String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
		return value instanceof String id ? id : UUID.randomUUID().toString();
	}
}
