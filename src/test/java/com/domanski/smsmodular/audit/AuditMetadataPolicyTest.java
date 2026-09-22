package com.domanski.smsmodular.audit;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.domanski.smsmodular.audit.service.AuditMetadataPolicy;
import com.domanski.smsmodular.common.api.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditMetadataPolicyTest {

	private final AuditMetadataPolicy policy = new AuditMetadataPolicy();

	@Test
	void acceptsOnlyBoundedCodes() {
		assertThat(policy.sanitize(Map.of("changedFields", List.of("NAME", "TIME_ZONE"),
				"fromStatus", "ACTIVE"))).containsKey("changedFields");
	}

	@Test
	void rejectsPersonalDataAndUnknownKeys() {
		assertThatThrownBy(() -> policy.sanitize(Map.of("email", "person@example.test")))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> policy.sanitize(Map.of("changedFields", List.of("person@example.test"))))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> policy.sanitize(Map.of("addedPermissions", List.of("USER_READ", "bad code"))))
				.isInstanceOf(ApiException.class);
	}
}
