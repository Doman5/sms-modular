package com.domanski.smsmodular.common.context;

import java.util.UUID;








public final class CorrelationContext {

	public static final String HEADER_NAME = "X-Correlation-Id";
	public static final String MDC_KEY = "correlationId";

	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

	private CorrelationContext() {
	}

	public static String current() {
		return CURRENT.get();
	}

	public static String requireCurrent() {
		String current = current();
		if (current == null) {
			throw new IllegalStateException("No correlation context is active");
		}
		return current;
	}

	public static String currentOrGenerate() {
		String current = current();
		return current == null ? UUID.randomUUID().toString() : current;
	}

	public static void set(String correlationId) {
		if (!isSafe(correlationId)) {
			throw new IllegalArgumentException("Correlation ID has an unsafe format");
		}
		CURRENT.set(correlationId);
	}

	public static void clear() {
		CURRENT.remove();
	}

	public static boolean isSafe(String correlationId) {
		return correlationId != null
			&& correlationId.length() <= 64
			&& correlationId.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}

	
	public static Scope open(String correlationId) {
		String previous = current();
		set(correlationId);
		return () -> {
			if (previous == null) {
				clear();
			} else {
				set(previous);
			}
		};
	}

	@FunctionalInterface
	public interface Scope extends AutoCloseable {
		@Override
		void close();
	}
}
