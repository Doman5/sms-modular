package com.domanski.smsmodular.tenancy.api.contract;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;








public final class TenantContext {

	private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

	private TenantContext() {
	}

	public static Optional<TenantId> current() {
		return Optional.ofNullable(CURRENT.get());
	}

	public static TenantId require() {
		TenantId tenantId = CURRENT.get();
		if (tenantId == null) {
			throw new TenantContextRequiredException();
		}
		return tenantId;
	}

	public static Scope open(TenantId tenantId) {
		Objects.requireNonNull(tenantId, "Tenant ID is required");
		TenantId previous = CURRENT.get();
		CURRENT.set(tenantId);
		return new Scope(previous, tenantId);
	}

	public static void runWith(TenantId tenantId, Runnable operation) {
		Objects.requireNonNull(operation, "Operation is required");
		try (Scope ignored = open(tenantId)) {
			operation.run();
		}
	}

	public static <T> T callWith(TenantId tenantId, Supplier<T> operation) {
		Objects.requireNonNull(operation, "Operation is required");
		try (Scope ignored = open(tenantId)) {
			return operation.get();
		}
	}

	
	public static void clear() {
		CURRENT.remove();
	}

	public static final class Scope implements AutoCloseable {

		private final TenantId previous;
		private final TenantId opened;
		private boolean closed;

		private Scope(TenantId previous, TenantId opened) {
			this.previous = previous;
			this.opened = opened;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			TenantId current = CURRENT.get();
			if (!opened.equals(current)) {
				
				
				CURRENT.remove();
				throw new IllegalStateException("Tenant context scope was closed out of order");
			}
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}
}
