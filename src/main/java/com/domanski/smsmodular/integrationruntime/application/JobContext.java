package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.MDC;






public final class JobContext {

	private static final String MDC_TENANT_ID = "tenantId";

	private final UUID eventId;
	private final TenantId tenantId;
	private final String correlationId;

	private JobContext(UUID eventId, TenantId tenantId, String correlationId) {
		this.eventId = Objects.requireNonNull(eventId, "Event ID is required");
		this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID is required");
		this.correlationId = Objects.requireNonNull(correlationId, "Correlation ID is required");
	}

	public UUID eventId() {
		return eventId;
	}

	public TenantId tenantId() {
		return tenantId;
	}

	public String correlationId() {
		return correlationId;
	}

	public static void run(OutboxMessage message, Task task) throws Exception {
		Objects.requireNonNull(message, "Outbox message is required");
		Objects.requireNonNull(task, "Job task is required");
		TenantContext.clear();
		CorrelationContext.clear();
		MDC.remove(MDC_TENANT_ID);
		try (TenantContext.Scope tenantScope = TenantContext.open(message.tenantId());
			CorrelationContext.Scope correlationScope = CorrelationContext.open(message.correlationId())) {
			MDC.put(MDC_TENANT_ID, message.tenantId().value().toString());
			MDC.put(CorrelationContext.MDC_KEY, message.correlationId());
			task.run(new JobContext(message.id(), message.tenantId(), message.correlationId()));
		} finally {
			MDC.remove(MDC_TENANT_ID);
			MDC.remove(CorrelationContext.MDC_KEY);
			TenantContext.clear();
			CorrelationContext.clear();
		}
	}

	public static <T> T call(OutboxMessage message, TaskSupplier<T> task) throws Exception {
		final Object[] result = new Object[1];
		run(message, context -> result[0] = task.get(context));
		@SuppressWarnings("unchecked")
		T typed = (T) result[0];
		return typed;
	}

	@FunctionalInterface
	public interface Task {
		void run(JobContext context) throws Exception;
	}

	@FunctionalInterface
	public interface TaskSupplier<T> {
		T get(JobContext context) throws Exception;
	}
}
