package com.domanski.smsmodular.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditMetadataPolicyTest {

	@Test
	void acceptsOnlyShortAllowListedLabelsAndCopiesThem() {
		Map<String, String> metadata = AuditMetadataPolicy.sanitize(Map.of(
			"operation", "SETTINGS_UPDATED",
			"changedFields", "name,locale"
		));

		assertThat(metadata).containsEntry("operation", "SETTINGS_UPDATED")
			.containsEntry("changedFields", "name,locale");
		assertThatThrownBy(() -> metadata.put("reason", "no"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsUnknownKeysAndSensitiveValues() {
		assertThatThrownBy(() -> AuditMetadataPolicy.sanitize(Map.of("payload", "anything")))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("disallowed");
		assertThatThrownBy(() -> AuditMetadataPolicy.sanitize(Map.of("reason", "Bearer secret-token")))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("disallowed");
	}
}
