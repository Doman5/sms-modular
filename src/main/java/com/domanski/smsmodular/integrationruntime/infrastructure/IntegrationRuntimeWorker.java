package com.domanski.smsmodular.integrationruntime.infrastructure;

import com.domanski.smsmodular.integrationruntime.application.OutboxProcessor;
import com.domanski.smsmodular.integrationruntime.infrastructure.configuration.IntegrationRuntimeProperties;
import com.domanski.smsmodular.tenancy.api.contract.ActiveTenantIdsPort;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.integration-runtime.enabled", havingValue = "true")
public class IntegrationRuntimeWorker {

	private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationRuntimeWorker.class);

	private final ActiveTenantIdsPort tenantIds;
	private final OutboxProcessor processor;
	private final IntegrationRuntimeProperties properties;
	private final ExecutorService executor;
	private final AtomicBoolean pollInProgress = new AtomicBoolean();
	private final String workerId = "worker-" + UUID.randomUUID();

	public IntegrationRuntimeWorker(
		ActiveTenantIdsPort tenantIds,
		OutboxProcessor processor,
		IntegrationRuntimeProperties properties,
		@Qualifier("integrationWorkerExecutor") ExecutorService executor
	) {
		this.tenantIds = tenantIds;
		this.processor = processor;
		this.properties = properties;
		this.executor = executor;
	}

	@Scheduled(fixedDelayString = "${app.integration-runtime.poll-interval:5s}")
	public void poll() {
		
		
		if (!pollInProgress.compareAndSet(false, true)) {
			return;
		}
		try {
			var activeTenants = tenantIds.activeTenantIds();
			CountDownLatch finished = new CountDownLatch(activeTenants.size());
			for (TenantId tenantId : activeTenants) {
				executor.execute(() -> {
					try {
						processor.processTenant(tenantId, workerId, properties.batchSize());
					} catch (RuntimeException exception) {
						LOGGER.error("Integration runtime tenant poll failed tenantId={} workerId={}", tenantId.value(), workerId);
					} finally {
						finished.countDown();
					}
				});
			}
			try {
				finished.await(properties.leaseDuration().toMillis(), TimeUnit.MILLISECONDS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		} finally {
			pollInProgress.set(false);
		}
	}

	@jakarta.annotation.PreDestroy
	public void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			executor.shutdownNow();
		}
	}
}
