package com.domanski.smsmodular.integrationruntime.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;


@Component
public class IntegrationRuntimeMetrics {

	private final Counter leased;
	private final Counter completed;
	private final Counter retries;
	private final Counter deadLetters;
	private final Counter recoveredLeases;
	private final Counter handlerFailures;
	private final Counter providerFailures;
	private final Timer handlerLatency;
	private final Timer providerLatency;
	private final MeterRegistry registry;
	private final AtomicLong lagMillis = new AtomicLong();

	public IntegrationRuntimeMetrics(MeterRegistry registry) {
		this.registry = registry;
		leased = Counter.builder("integration_runtime_outbox_leased_total").register(registry);
		completed = Counter.builder("integration_runtime_outbox_completed_total").register(registry);
		retries = Counter.builder("integration_runtime_outbox_retries_total").register(registry);
		deadLetters = Counter.builder("integration_runtime_outbox_dead_letters_total").register(registry);
		recoveredLeases = Counter.builder("integration_runtime_outbox_lease_recovery_total").register(registry);
		handlerFailures = Counter.builder("integration_runtime_handler_failures_total").register(registry);
		providerFailures = Counter.builder("integration_runtime_provider_failures_total").register(registry);
		handlerLatency = Timer.builder("integration_runtime_handler_latency").publishPercentiles(0.5, 0.95, 0.99).register(registry);
		providerLatency = Timer.builder("integration_runtime_provider_latency").publishPercentiles(0.5, 0.95, 0.99).register(registry);
		Gauge.builder("integration_runtime_outbox_lag_millis", lagMillis, AtomicLong::get).register(registry);
	}

	public void leased(long value) { leased.increment(value); }
	public void completed() { completed.increment(); }
	public void retry() { retries.increment(); }
	public void deadLetter() { deadLetters.increment(); }
	public void recoveredLease() { recoveredLeases.increment(); }
	public void handlerFailure() { handlerFailures.increment(); }
	public void providerFailure() { providerFailures.increment(); }
	public void providerStatus(String status) {
		String safeStatus = status == null || status.isBlank()
			? "UNKNOWN"
			: status.trim().replaceAll("[^A-Za-z0-9_-]", "_").toUpperCase(Locale.ROOT);
		registry.counter("integration_runtime_provider_status_total", "status", safeStatus).increment();
	}
	public void recordLag(Duration lag) { lagMillis.set(Math.max(0, lag.toMillis())); }
	public Timer.Sample startHandlerTimer() { return Timer.start(); }
	public void stopHandlerTimer(Timer.Sample sample) { sample.stop(handlerLatency); }
	public Timer.Sample startProviderTimer() { return Timer.start(); }
	public void stopProviderTimer(Timer.Sample sample) { sample.stop(providerLatency); }

	public Snapshot snapshot() {
		return new Snapshot((long) leased.count(), (long) completed.count(), (long) retries.count(),
			(long) deadLetters.count(), (long) recoveredLeases.count(), (long) handlerFailures.count(),
			(long) providerFailures.count(), lagMillis.get());
	}

	public record Snapshot(long leased, long completed, long retries, long deadLetters,
		long recoveredLeases, long handlerFailures, long providerFailures, long lagMillis) { }
}
