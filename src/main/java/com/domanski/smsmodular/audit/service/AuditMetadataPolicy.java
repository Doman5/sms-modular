package com.domanski.smsmodular.audit.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.domanski.smsmodular.common.api.ApiException;

@Component
public class AuditMetadataPolicy {

	private static final Set<String> LIST_KEYS = Set.of("changedFields", "addedPermissions", "removedPermissions");
	private static final Set<String> VALUE_KEYS = Set.of("fromStatus", "toStatus");

	public Map<String, Object> sanitize(Map<String, Object> metadata) {
		if (metadata == null || metadata.isEmpty()) {
			return Map.of();
		}
		if (metadata.size() > 5) {
			throw invalid();
		}
		Map<String, Object> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : metadata.entrySet()) {
			Object value = entry.getValue();
			if (LIST_KEYS.contains(entry.getKey()) && value instanceof List<?> list
					&& list.size() <= 20 && list.stream().allMatch(this::safeCode)) {
				result.put(entry.getKey(), List.copyOf(list));
			} else if (VALUE_KEYS.contains(entry.getKey()) && safeCode(value)) {
				result.put(entry.getKey(), value);
			} else {
				throw invalid();
			}
		}
		return Map.copyOf(result);
	}

	private boolean safeCode(Object value) {
		return value instanceof String code && code.matches("[A-Z][A-Z0-9_]{0,63}");
	}

	private ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_METADATA_INVALID", "Audit metadata is invalid");
	}
}
