package com.domanski.smsmodular.audit.api.contract;

import java.util.Locale;
import java.util.UUID;









public record ActorRef(String type, UUID id) {

	public static final String USER = "USER";
	public static final String SYSTEM = "SYSTEM";
	public static final String PLATFORM = "PLATFORM";
	public static final String LEGACY_SYSTEM = "LEGACY_SYSTEM";

	public ActorRef {
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("Actor type is required");
		}
		type = type.trim().toUpperCase(Locale.ROOT);
		if (!type.matches("[A-Z][A-Z0-9_]{1,31}")) {
			throw new IllegalArgumentException("Actor type has an invalid format");
		}
	}

	public static ActorRef user(UUID id) {
		return new ActorRef(USER, id);
	}

	public static ActorRef system() {
		return new ActorRef(SYSTEM, null);
	}

	public static ActorRef platform(UUID id) {
		return new ActorRef(PLATFORM, id);
	}

	public static ActorRef legacySystem() {
		return new ActorRef(LEGACY_SYSTEM, null);
	}

	public boolean isPlatformActor() {
		return PLATFORM.equals(type);
	}
}
