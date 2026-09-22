package com.domanski.smsmodular.integrationruntime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

	@Test
	void capsExponentialBackoffAndDeadLettersAtTheConfiguredAttempt() {
		RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(2), Duration.ofSeconds(5), 0);
		Instant now = Instant.parse("2026-01-01T00:00:00Z");

		assertThat(policy.nextAvailableAt(now, 1)).isEqualTo(now.plusSeconds(2));
		assertThat(policy.nextAvailableAt(now, 2)).isEqualTo(now.plusSeconds(4));
		assertThat(policy.nextAvailableAt(now, 3)).isEqualTo(now.plusSeconds(5));
		assertThat(policy.shouldDeadLetter(2)).isFalse();
		assertThat(policy.shouldDeadLetter(3)).isTrue();
	}

	@Test
	void rejectsInvalidAttemptAndConfiguration() {
		RetryPolicy policy = new RetryPolicy(2, Duration.ofSeconds(1), Duration.ofSeconds(2), 0);
		assertThatThrownBy(() -> policy.nextAvailableAt(Instant.now(), 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(2), 0))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
