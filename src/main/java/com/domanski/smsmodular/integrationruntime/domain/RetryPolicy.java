package com.domanski.smsmodular.integrationruntime.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.random.RandomGenerator;


public final class RetryPolicy {

	private final int maxAttempts;
	private final Duration initialBackoff;
	private final Duration maxBackoff;
	private final double jitterRatio;
	private final RandomGenerator random;

	public RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, double jitterRatio) {
		this(maxAttempts, initialBackoff, maxBackoff, jitterRatio, RandomGenerator.getDefault());
	}

	public RetryPolicy(
		int maxAttempts,
		Duration initialBackoff,
		Duration maxBackoff,
		double jitterRatio,
		RandomGenerator random
	) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("Maximum attempts must be positive");
		}
		if (initialBackoff == null || initialBackoff.isNegative() || initialBackoff.isZero()) {
			throw new IllegalArgumentException("Initial backoff must be positive");
		}
		if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
			throw new IllegalArgumentException("Maximum backoff must not precede initial backoff");
		}
		if (jitterRatio < 0 || jitterRatio > 1) {
			throw new IllegalArgumentException("Jitter ratio must be between zero and one");
		}
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
		this.maxBackoff = maxBackoff;
		this.jitterRatio = jitterRatio;
		this.random = Objects.requireNonNull(random, "Random generator is required");
	}

	public int maxAttempts() {
		return maxAttempts;
	}

	public boolean shouldDeadLetter(int attempt) {
		return attempt >= maxAttempts;
	}

	
	public Instant nextAvailableAt(Instant now, int attempt) {
		Objects.requireNonNull(now, "Current time is required");
		if (attempt < 1) {
			throw new IllegalArgumentException("Attempt must be positive");
		}
		long multiplier = 1L << Math.min(attempt - 1, 30);
		long baseMillis;
		try {
			baseMillis = Math.multiplyExact(initialBackoff.toMillis(), multiplier);
		} catch (ArithmeticException ignored) {
			baseMillis = Long.MAX_VALUE;
		}
		long cappedMillis = Math.min(baseMillis, maxBackoff.toMillis());
		double factor = jitterRatio == 0 ? 1 : 1 - jitterRatio + random.nextDouble() * 2 * jitterRatio;
		long jittered = Math.max(1L, Math.min(maxBackoff.toMillis(), Math.round(cappedMillis * factor)));
		return now.plusMillis(jittered);
	}
}
