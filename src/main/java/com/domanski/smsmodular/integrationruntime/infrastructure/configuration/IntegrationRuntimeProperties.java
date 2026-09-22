package com.domanski.smsmodular.integrationruntime.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "app.integration-runtime")
public record IntegrationRuntimeProperties(
	boolean enabled,
	int batchSize,
	Duration leaseDuration,
	Duration pollInterval,
	int maxAttempts,
	Duration initialBackoff,
	Duration maxBackoff,
	double jitterRatio,
	Duration handlerTimeout,
	int workerThreads,
	SmsProperties sms
) {

	public IntegrationRuntimeProperties {
		batchSize = batchSize <= 0 ? 25 : batchSize;
		leaseDuration = positive(leaseDuration, Duration.ofSeconds(60));
		pollInterval = positive(pollInterval, Duration.ofSeconds(5));
		maxAttempts = maxAttempts <= 0 ? 8 : maxAttempts;
		initialBackoff = positive(initialBackoff, Duration.ofSeconds(2));
		maxBackoff = positive(maxBackoff, Duration.ofMinutes(15));
		handlerTimeout = positive(handlerTimeout, Duration.ofSeconds(30));
		if (maxBackoff.compareTo(initialBackoff) < 0) {
			throw new IllegalArgumentException("Integration max-backoff cannot precede initial-backoff");
		}
		if (leaseDuration.compareTo(handlerTimeout) <= 0) {
			throw new IllegalArgumentException("Integration lease-duration must exceed handler-timeout");
		}
		if (jitterRatio < 0 || jitterRatio > 1) {
			throw new IllegalArgumentException("Integration jitter-ratio must be between zero and one");
		}
		workerThreads = workerThreads <= 0 ? 4 : workerThreads;
		sms = sms == null ? new SmsProperties(false, "disabled", false) : sms;
		if (sms.enabled()) {
			throw new IllegalStateException(
				"SMS transport cannot be enabled until a signed provider adapter is configured"
			);
		}
	}

	private static Duration positive(Duration value, Duration fallback) {
		return value == null || value.isZero() || value.isNegative() ? fallback : value;
	}

	public record SmsProperties(boolean enabled, String provider, boolean webhookEnabled) {
		public SmsProperties {
			provider = provider == null || provider.isBlank() ? "disabled" : provider.trim();
		}
	}
}
