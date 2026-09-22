package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.domain.RetryPolicy;
import com.domanski.smsmodular.integrationruntime.infrastructure.configuration.IntegrationRuntimeProperties;
import com.domanski.smsmodular.integrationruntime.infrastructure.metrics.IntegrationRuntimeMetrics;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class OutboxProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(OutboxProcessor.class);

	private final OutboxLeaseService leaseService;
	private final OutboxHandlerRegistry handlers;
	private final HandlerExecutionService execution;
	private final RetryPolicy retryPolicy;
	private final Clock clock;
	private final java.time.Duration leaseDuration;
	private final java.time.Duration handlerTimeout;
	private final ExecutorService handlerExecutor;
	private final IntegrationRuntimeMetrics metrics;
	private final ExecutorService messageExecutor;
	private final ScheduledExecutorService leaseRenewer;
	private final int maxParallelMessages;

	public OutboxProcessor(
		OutboxLeaseService leaseService,
		OutboxHandlerRegistry handlers,
		HandlerExecutionService execution,
		RetryPolicy retryPolicy,
		Clock clock,
		IntegrationRuntimeProperties properties,
		@Qualifier("integrationHandlerExecutor") ExecutorService handlerExecutor,
		IntegrationRuntimeMetrics metrics,
		@Qualifier("integrationMessageExecutor") ExecutorService messageExecutor,
		@Qualifier("integrationLeaseRenewer") ScheduledExecutorService leaseRenewer
	) {
		this.leaseService = leaseService;
		this.handlers = handlers;
		this.execution = execution;
		this.retryPolicy = retryPolicy;
		this.clock = clock;
		this.leaseDuration = properties.leaseDuration();
		this.handlerTimeout = properties.handlerTimeout();
		this.handlerExecutor = handlerExecutor;
		this.metrics = metrics;
		this.messageExecutor = messageExecutor;
		this.leaseRenewer = leaseRenewer;
		this.maxParallelMessages = properties.workerThreads();
	}

	public int processTenant(TenantId tenantId, String workerId, int batchSize) {
		Instant now = clock.instant();
		List<OutboxMessage> messages;
		try (TenantContext.Scope ignored = TenantContext.open(tenantId)) {
			messages = leaseService.lease(tenantId, workerId, now, now.plus(leaseDuration),
				Math.min(batchSize, maxParallelMessages));
		}
		metrics.leased(messages.size());
		if (messages.isEmpty()) {
			return 0;
		}
		Instant processedAt = clock.instant();
		messages.stream().map(OutboxMessage::availableAt)
			.min(Instant::compareTo)
			.ifPresent(available -> metrics.recordLag(java.time.Duration.between(available, processedAt)));
		int safeCount = 0;
		var futures = messages.stream()
			.map(message -> CompletableFuture.supplyAsync(() -> processOne(message, workerId), messageExecutor))
			.toList();
		for (var future : futures) {
			if (Boolean.TRUE.equals(future.join())) {
				safeCount++;
			}
		}
		return safeCount;
	}

	private boolean processOne(OutboxMessage message, String workerId) {
		OutboxHandler handler = handlers.find(message);
		if (handler == null) {
			transitionToDeadLetter(message, workerId, "UNSUPPORTED_EVENT_VERSION");
			return false;
		}
		ScheduledFuture<?> renewal = scheduleRenewal(message, workerId);
		try {
			executeWithTimeout(message, handler);
			try (TenantContext.Scope ignored = TenantContext.open(message.tenantId())) {
				if (leaseService.complete(message.tenantId(), message.id(), workerId, clock.instant())) {
					metrics.completed();
				}
			}
			return true;
		} catch (Exception exception) {
			metrics.handlerFailure();
			String errorCode = safeErrorCode(exception);
			Instant now = clock.instant();
			try (TenantContext.Scope ignored = TenantContext.open(message.tenantId())) {
				if (retryPolicy.shouldDeadLetter(message.attempt())) {
					if (leaseService.deadLetter(message.tenantId(), message.id(), workerId, now, errorCode)) {
						metrics.deadLetter();
					}
				} else {
					if (leaseService.retry(
						message.tenantId(), message.id(), workerId, now,
						retryPolicy.nextAvailableAt(now, message.attempt()), errorCode
					)) {
						metrics.retry();
					}
				}
			}
			LOGGER.warn(
				"Outbox handler failed tenantId={} correlationId={} eventId={} errorCode={}",
				message.tenantId().value(), message.correlationId(), message.id(), errorCode
			);
			return false;
		} finally {
			renewal.cancel(false);
		}
	}

	private ScheduledFuture<?> scheduleRenewal(OutboxMessage message, String workerId) {
		long period = Math.max(100L, leaseDuration.toMillis() / 3L);
		return leaseRenewer.scheduleAtFixedRate(() -> {
			try (TenantContext.Scope ignored = TenantContext.open(message.tenantId())) {
				leaseService.renew(
					message.tenantId(), message.id(), workerId, clock.instant(), clock.instant().plus(leaseDuration)
				);
			} catch (RuntimeException exception) {
				LOGGER.warn("Outbox lease renewal failed tenantId={} eventId={}", message.tenantId().value(), message.id());
			}
		}, period, period, TimeUnit.MILLISECONDS);
	}

	private boolean executeWithTimeout(OutboxMessage message, OutboxHandler handler) throws Exception {
		var sample = metrics.startHandlerTimer();
		Future<Boolean> future = handlerExecutor.submit(() -> {
			try {
				return JobContext.call(message, context -> execution.execute(message, handler, context));
			} catch (Exception exception) {
				throw exception;
			}
		});
		try {
			return future.get(handlerTimeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException exception) {
			
			
			
			LOGGER.warn("Outbox handler exceeded configured timeout; waiting for safe completion");
			try {
				return awaitCompletionSafely(future);
			} catch (ExecutionException completionFailure) {
				Throwable cause = completionFailure.getCause();
				if (cause instanceof Exception checked) {
					throw checked;
				}
				throw new IntegrationRuntimeFailure("HANDLER_FAILURE", "Handler execution failed.");
			}
		} catch (InterruptedException exception) {
			try {
				return awaitCompletionSafely(future);
			} catch (ExecutionException completionFailure) {
				Throwable cause = completionFailure.getCause();
				if (cause instanceof Exception checked) {
					throw checked;
				}
				throw new IntegrationRuntimeFailure("HANDLER_FAILURE", "Handler execution failed.");
			}
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof Exception checked) {
				throw checked;
			}
			throw new IntegrationRuntimeFailure("HANDLER_FAILURE", "Handler execution failed.");
		} finally {
			
			
			
			if (Thread.currentThread().isInterrupted()) {
				Thread.currentThread().interrupt();
			}
			metrics.stopHandlerTimer(sample);
		}
	}

	private boolean awaitCompletionSafely(Future<Boolean> future) throws ExecutionException {
		boolean interrupted = false;
		try {
			for (;;) {
				try {
					return future.get();
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void transitionToDeadLetter(OutboxMessage message, String workerId, String errorCode) {
		try (TenantContext.Scope ignored = TenantContext.open(message.tenantId())) {
			if (leaseService.deadLetter(message.tenantId(), message.id(), workerId, clock.instant(), errorCode)) {
				metrics.deadLetter();
			}
		}
	}

	private String safeErrorCode(Exception exception) {
		if (exception instanceof IntegrationRuntimeFailure failure) {
			return failure.errorCode();
		}
		String simpleName = exception.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]", "");
		if (simpleName.isBlank()) {
			return "HANDLER_FAILURE";
		}
		String code = (simpleName + "_FAILURE").toUpperCase(java.util.Locale.ROOT);
		return code.length() > 64 ? "HANDLER_FAILURE" : code;
	}
}
