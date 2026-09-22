package com.domanski.smsmodular.audit.api;

import java.util.Map;
import java.util.UUID;

public record AuditCommand(UUID tenantId, AuditCallContext context, String module, String action,
		String targetType, UUID targetId, AuditResult result, Map<String, Object> metadata) {

	public static AuditCommand success(UUID tenantId, AuditCallContext context, String module,
			String action, String targetType, UUID targetId, Map<String, Object> metadata) {
		return new AuditCommand(tenantId, context, module, action, targetType, targetId,
				AuditResult.SUCCESS, metadata);
	}
}
