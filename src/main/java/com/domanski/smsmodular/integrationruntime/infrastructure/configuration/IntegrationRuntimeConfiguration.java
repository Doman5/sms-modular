package com.domanski.smsmodular.integrationruntime.infrastructure.configuration;

import com.domanski.smsmodular.integrationruntime.domain.RetryPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(IntegrationRuntimeProperties.class)
@EnableScheduling
public class IntegrationRuntimeConfiguration {

	@Bean
	public RetryPolicy integrationRetryPolicy(IntegrationRuntimeProperties properties) {
		return new RetryPolicy(
			properties.maxAttempts(),
			properties.initialBackoff(),
			properties.maxBackoff(),
			properties.jitterRatio()
		);
	}

	@Bean(name = "integrationHandlerExecutor", destroyMethod = "shutdown")
	public ExecutorService integrationHandlerExecutor(IntegrationRuntimeProperties properties) {
		return boundedExecutor(properties.workerThreads());
	}

	@Bean(name = "integrationWorkerExecutor", destroyMethod = "shutdown")
	public ExecutorService integrationWorkerExecutor(IntegrationRuntimeProperties properties) {
		return boundedExecutor(properties.workerThreads());
	}

	@Bean(name = "integrationMessageExecutor", destroyMethod = "shutdown")
	public ExecutorService integrationMessageExecutor(IntegrationRuntimeProperties properties) {
		return boundedExecutor(properties.workerThreads());
	}

	@Bean(name = "integrationLeaseRenewer", destroyMethod = "shutdown")
	public ScheduledExecutorService integrationLeaseRenewer(IntegrationRuntimeProperties properties) {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
			Math.max(1, properties.workerThreads() / 2)
		);
		executor.setRemoveOnCancelPolicy(true);
		return executor;
	}

	private ExecutorService boundedExecutor(int threads) {
		return new ThreadPoolExecutor(
			threads,
			threads,
			0L,
			TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(Math.max(1, threads * 4)),
			new ThreadPoolExecutor.CallerRunsPolicy()
		);
	}
}
