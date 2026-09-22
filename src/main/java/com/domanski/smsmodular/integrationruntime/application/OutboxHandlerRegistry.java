package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;


@Component
public final class OutboxHandlerRegistry {

	private final Map<Key, OutboxHandler> handlers;

	public OutboxHandlerRegistry(List<OutboxHandler> handlers) {
		Map<Key, OutboxHandler> byKey = new HashMap<>();
		for (OutboxHandler handler : handlers) {
			Objects.requireNonNull(handler, "Outbox handler is required");
			if (handler.topic() == null || handler.topic().isBlank() || handler.payloadVersion() < 1) {
				throw new IllegalStateException("Outbox handler has an invalid topic or version");
			}
			Key key = new Key(handler.topic(), handler.payloadVersion());
			if (byKey.putIfAbsent(key, handler) != null) {
				throw new IllegalStateException("Duplicate outbox handler for topic and version");
			}
		}
		this.handlers = Map.copyOf(byKey);
	}

	public OutboxHandler find(OutboxMessage message) {
		return handlers.get(new Key(message.topic(), message.payloadVersion()));
	}

	public int size() {
		return handlers.size();
	}

	private record Key(String topic, int version) {
	}
}
