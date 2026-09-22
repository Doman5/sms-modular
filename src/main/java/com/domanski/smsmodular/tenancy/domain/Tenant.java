package com.domanski.smsmodular.tenancy.domain;

import com.domanski.smsmodular.common.api.ApiProblemCode;
import com.domanski.smsmodular.common.domain.DomainException;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;


public final class Tenant {

	private static final int SLUG_MAX_LENGTH = 64;
	private static final int NAME_MAX_LENGTH = 255;

	private final TenantId id;
	private final String slug;
	private String name;
	private TenantStatus status;
	private String timezone;
	private String locale;
	private final Instant createdAt;
	private Instant updatedAt;
	private Instant closedAt;

	private Tenant(
		TenantId id,
		String slug,
		String name,
		TenantStatus status,
		String timezone,
		String locale,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt
	) {
		this.id = Objects.requireNonNull(id, "Tenant ID is required");
		this.slug = validateSlug(slug);
		this.name = validateName(name);
		this.status = Objects.requireNonNull(status, "Tenant status is required");
		this.timezone = validateTimezone(timezone);
		this.locale = validateLocale(locale);
		this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp is required");
		this.updatedAt = Objects.requireNonNull(updatedAt, "Updated timestamp is required");
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("Updated timestamp cannot precede creation timestamp");
		}
		this.closedAt = closedAt;
		validateClosedAt(status, closedAt);
	}

	public static Tenant create(
		String slug,
		String name,
		String timezone,
		String locale,
		Clock clock
	) {
		Objects.requireNonNull(clock, "Clock is required");
		Instant now = clock.instant();
		return new Tenant(
			new TenantId(UUID.randomUUID()),
			slug,
			name,
			TenantStatus.ACTIVE,
			timezone,
			locale,
			now,
			now,
			null
		);
	}

	public static Tenant rehydrate(
		TenantId id,
		String slug,
		String name,
		TenantStatus status,
		String timezone,
		String locale,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt
	) {
		return new Tenant(id, slug, name, status, timezone, locale, createdAt, updatedAt, closedAt);
	}

	public TenantId id() {
		return id;
	}

	public String slug() {
		return slug;
	}

	public String name() {
		return name;
	}

	public TenantStatus status() {
		return status;
	}

	public String timezone() {
		return timezone;
	}

	public String locale() {
		return locale;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	public Instant closedAt() {
		return closedAt;
	}

	public void updateSettings(String name, String timezone, String locale, Clock clock) {
		Objects.requireNonNull(clock, "Clock is required");
		if (status == TenantStatus.CLOSED) {
			throw new TenantStatusTransitionException(
				"Closed tenants cannot be changed.",
				ApiProblemCode.TENANT_CLOSED
			);
		}
		this.name = validateName(name);
		this.timezone = validateTimezone(timezone);
		this.locale = validateLocale(locale);
		this.updatedAt = clock.instant();
	}

	public void suspend(Clock clock) {
		Objects.requireNonNull(clock, "Clock is required");
		if (status != TenantStatus.ACTIVE) {
			throw invalidTransition("Only an active tenant can be suspended.");
		}
		status = TenantStatus.SUSPENDED;
		updatedAt = clock.instant();
	}

	public void activate(Clock clock) {
		Objects.requireNonNull(clock, "Clock is required");
		if (status != TenantStatus.SUSPENDED) {
			throw invalidTransition("Only a suspended tenant can be activated.");
		}
		status = TenantStatus.ACTIVE;
		updatedAt = clock.instant();
	}

	public void close(Clock clock) {
		Objects.requireNonNull(clock, "Clock is required");
		if (status == TenantStatus.CLOSED) {
			throw new TenantStatusTransitionException(
				"A closed tenant cannot be reopened or closed again.",
				ApiProblemCode.TENANT_CLOSED
			);
		}
		Instant now = clock.instant();
		status = TenantStatus.CLOSED;
		closedAt = now;
		updatedAt = now;
	}

	public void requireActiveForCommand() {
		if (status == TenantStatus.SUSPENDED) {
			throw new TenantStatusTransitionException(
				"Business commands are unavailable while the tenant is suspended.",
				ApiProblemCode.TENANT_SUSPENDED
			);
		}
		if (status == TenantStatus.CLOSED) {
			throw new TenantStatusTransitionException(
				"Business commands are unavailable for a closed tenant.",
				ApiProblemCode.TENANT_CLOSED
			);
		}
	}

	private static TenantStatusTransitionException invalidTransition(String message) {
		return new TenantStatusTransitionException(message, ApiProblemCode.TENANT_STATUS_TRANSITION_INVALID);
	}

	private static String validateSlug(String value) {
		String normalized = requireText(value, "Tenant slug").toLowerCase(Locale.ROOT);
		if (normalized.length() > SLUG_MAX_LENGTH || !normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
			throw new DomainException("Tenant slug must contain only lowercase letters, digits and single hyphens.");
		}
		return normalized;
	}

	private static String validateName(String value) {
		String normalized = requireText(value, "Tenant name");
		if (normalized.length() > NAME_MAX_LENGTH) {
			throw new DomainException("Tenant name is too long.");
		}
		return normalized;
	}

	private static String validateTimezone(String value) {
		String normalized = requireText(value, "Tenant timezone");
		try {
			ZoneId zoneId = ZoneId.of(normalized);
			if (zoneId.getId().startsWith("+") || zoneId.getId().startsWith("-")) {
				throw new IllegalArgumentException("Offset is not an IANA timezone");
			}
		} catch (RuntimeException exception) {
			throw new DomainException("Tenant timezone must be a valid IANA timezone.");
		}
		return normalized;
	}

	private static String validateLocale(String value) {
		String normalized = requireText(value, "Tenant locale");
		if (normalized.contains("_") || !normalized.matches("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*")) {
			throw new DomainException("Tenant locale must be a valid BCP 47 language tag.");
		}
		Locale parsed = Locale.forLanguageTag(normalized);
		if (parsed.getLanguage().isBlank()) {
			throw new DomainException("Tenant locale must be a valid BCP 47 language tag.");
		}
		return parsed.toLanguageTag();
	}

	private static String requireText(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new DomainException(label + " is required.");
		}
		return value.trim();
	}

	private static void validateClosedAt(TenantStatus status, Instant closedAt) {
		if (status == TenantStatus.CLOSED && closedAt == null) {
			throw new IllegalArgumentException("Closed tenant requires closedAt");
		}
		if (status != TenantStatus.CLOSED && closedAt != null) {
			throw new IllegalArgumentException("Only a closed tenant may have closedAt");
		}
	}
}
