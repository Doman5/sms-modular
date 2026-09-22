package com.domanski.smsmodular.integrationruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.OutboxStatus;
import com.domanski.smsmodular.integrationruntime.domain.RetryPolicy;
import com.domanski.smsmodular.integrationruntime.infrastructure.configuration.IntegrationRuntimeProperties;
import com.domanski.smsmodular.integrationruntime.infrastructure.metrics.IntegrationRuntimeMetrics;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboxProcessorTest {

	private final ExecutorService handlerExecutor = Executors.newFixedThreadPool(2);
	private final ExecutorService messageExecutor = Executors.newFixedThreadPool(2);
	private final ScheduledExecutorService renewer = Executors.newScheduledThreadPool(1);

	@AfterEach
	void shutDown() {
		handlerExecutor.shutdownNow();
		messageExecutor.shutdownNow();
		renewer.shutdownNow();
		TenantContext.clear();
	}

	@Test
	void processesLeasedMessagesInParallelAndCompletesEachLease() throws Exception {
		TenantId tenant = TenantId.of(UUID.randomUUID());
		OutboxMessage first = message(tenant, 1);
		OutboxMessage second = message(tenant, 1);
		OutboxLeaseService leases = mock(OutboxLeaseService.class);
		HandlerExecutionService execution = mock(HandlerExecutionService.class);
		OutboxHandler handler = handler("TENANT.CREATED", 1);
		OutboxHandlerRegistry registry = new OutboxHandlerRegistry(List.of(handler));
		CountDownLatch started = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger active = new AtomicInteger();
		AtomicInteger maxActive = new AtomicInteger();
		when(leases.lease(eq(tenant), eq("worker-1"), any(), any(), anyInt())).thenReturn(List.of(first, second));
		when(execution.execute(any(), eq(handler), any())).thenAnswer(invocation -> {
			int now = active.incrementAndGet();
			maxActive.accumulateAndGet(now, Math::max);
			started.countDown();
			assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
			active.decrementAndGet();
			return true;
		});

		OutboxProcessor processor = processor(leases, registry, execution, 3, Duration.ofMillis(500));
		var processing = java.util.concurrent.CompletableFuture.supplyAsync(
			() -> processor.processTenant(tenant, "worker-1", 10));
		assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
		release.countDown();
		assertThat(processing.get(3, TimeUnit.SECONDS)).isEqualTo(2);
		assertThat(maxActive.get()).isEqualTo(2);
		verify(leases).complete(eq(tenant), eq(first.id()), eq("worker-1"), any());
		verify(leases).complete(eq(tenant), eq(second.id()), eq("worker-1"), any());
	}

	@Test
	void retriesFailuresWithSafeCodeAndDeadLettersAfterMaxAttempt() throws Exception {
		TenantId tenant = TenantId.of(UUID.randomUUID());
		OutboxMessage retryable = message(tenant, 1);
		OutboxMessage terminal = message(tenant, 3);
		OutboxLeaseService leases = mock(OutboxLeaseService.class);
		HandlerExecutionService execution = mock(HandlerExecutionService.class);
		OutboxHandler handler = handler("TENANT.CREATED", 1);
		when(leases.lease(eq(tenant), eq("worker-1"), any(), any(), anyInt())).thenReturn(List.of(retryable, terminal));
		when(execution.execute(any(), eq(handler), any()))
			.thenThrow(new IntegrationRuntimeFailure("PROVIDER_UNAVAILABLE", "safe"));

		OutboxProcessor processor = processor(leases, new OutboxHandlerRegistry(List.of(handler)), execution, 3, Duration.ofMillis(500));
		assertThat(processor.processTenant(tenant, "worker-1", 2)).isZero();
		verify(leases).retry(eq(tenant), eq(retryable.id()), eq("worker-1"), any(), any(), eq("PROVIDER_UNAVAILABLE"));
		verify(leases).deadLetter(eq(tenant), eq(terminal.id()), eq("worker-1"), any(), eq("PROVIDER_UNAVAILABLE"));
	}

	@Test
	void timeoutWaitsForTheOriginalHandlerBeforeCompletingOrRetrying() throws Exception {
		TenantId tenant = TenantId.of(UUID.randomUUID());
		OutboxMessage event = message(tenant, 1);
		OutboxLeaseService leases = mock(OutboxLeaseService.class);
		HandlerExecutionService execution = mock(HandlerExecutionService.class);
		OutboxHandler handler = handler("TENANT.CREATED", 1);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(leases.lease(eq(tenant), eq("worker-1"), any(), any(), anyInt())).thenReturn(List.of(event));
		when(execution.execute(any(), eq(handler), any())).thenAnswer(invocation -> {
			started.countDown();
			release.await(2, TimeUnit.SECONDS);
			return true;
		});

		OutboxProcessor processor = processor(leases, new OutboxHandlerRegistry(List.of(handler)), execution, 3, Duration.ofMillis(100));
		var processing = java.util.concurrent.CompletableFuture.supplyAsync(
			() -> processor.processTenant(tenant, "worker-1", 1));
		assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
		Thread.sleep(180);
		org.mockito.Mockito.verify(leases, org.mockito.Mockito.never())
			.retry(any(), any(), any(), any(), any(), any());
		release.countDown();
		assertThat(processing.get(3, TimeUnit.SECONDS)).isEqualTo(1);
		verify(leases).complete(eq(tenant), eq(event.id()), eq("worker-1"), any());
	}

	private OutboxProcessor processor(
		OutboxLeaseService leases,
		OutboxHandlerRegistry registry,
		HandlerExecutionService execution,
		int maxAttempts,
		Duration handlerTimeout
	) {
		IntegrationRuntimeProperties properties = new IntegrationRuntimeProperties(
			false, 4, Duration.ofSeconds(1), Duration.ofMillis(10), maxAttempts,
			Duration.ofMillis(10), Duration.ofSeconds(1), 0, handlerTimeout, 2,
			new IntegrationRuntimeProperties.SmsProperties(false, "disabled", false)
		);
		return new OutboxProcessor(
			leases, registry, execution,
			new RetryPolicy(maxAttempts, Duration.ofMillis(10), Duration.ofSeconds(1), 0),
			Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), properties,
			handlerExecutor, new IntegrationRuntimeMetrics(new SimpleMeterRegistry()), messageExecutor, renewer
		);
	}

	private OutboxHandler handler(String topic, int version) {
		return new OutboxHandler() {
			@Override public String topic() { return topic; }
			@Override public int payloadVersion() { return version; }
			@Override public void handle(OutboxMessage message, JobContext context) { }
		};
	}

	private OutboxMessage message(TenantId tenant, int attempt) {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		return new OutboxMessage(
			UUID.randomUUID(), tenant, "TENANT.CREATED", "TENANT", UUID.randomUUID(), 1,
			"{}", OutboxStatus.PROCESSING, attempt, now, "worker-1", now.plusSeconds(1),
			"processor-correlation", UUID.randomUUID().toString(), now, now, null
		);
	}
}
