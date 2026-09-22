package com.domanski.smsmodular.audit.domain;

import com.domanski.smsmodular.common.api.ApiProblemCode;
import com.domanski.smsmodular.common.domain.DomainException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;






public final class AuditMetadataPolicy {

	public static final Set<String> ALLOWED_KEYS = Set.of(
		"source", "reason", "status", "previousStatus", "newStatus", "changedFields", "operation"
	);

	private static final Pattern SENSITIVE_VALUE = Pattern.compile(
		"(?i)(password|secret|token|authorization|bearer|prompt|payload|sms|message|phone|email|content|\\+?\\d{7,})"
	);
	private static final int MAX_VALUE_LENGTH = 256;

	private AuditMetadataPolicy() {
	}

	public static Map<String, String> sanitize(Map<String, String> metadata) {
		if (metadata == null || metadata.isEmpty()) {
			return Map.of();
		}
		if (metadata.size() > ALLOWED_KEYS.size()) {
			throw rejected();
		}
		Map<String, String> sanitized = new LinkedHashMap<>();
		metadata.forEach((key, value) -> {
			if (key == null || !ALLOWED_KEYS.contains(key) || value == null
				|| value.isBlank() || value.length() > MAX_VALUE_LENGTH
				|| SENSITIVE_VALUE.matcher(value).find()) {
				throw rejected();
			}
			sanitized.put(key, value.trim());
		});
		return Map.copyOf(sanitized);
	}

	private static DomainException rejected() {
		return new DomainException(
			ApiProblemCode.VALIDATION_ERROR,
			"Audit metadata contains a disallowed key or value."
		);
	}
}
