package com.domanski.smsmodular.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "platform_accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformAccount {

	@Id
	@Setter(AccessLevel.NONE)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Setter(AccessLevel.NONE)
	@Column(name = "normalized_email", nullable = false, length = 254)
	private String normalizedEmail;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AccountStatus status;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "session_version", nullable = false)
	private long sessionVersion;

	@Column(name = "failed_attempts", nullable = false)
	private int failedAttempts;

	@Column(name = "first_failed_at")
	private Instant firstFailedAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public PlatformAccount(UUID id, String normalizedEmail, String passwordHash, Instant now) {
		this.id = id;
		this.normalizedEmail = normalizedEmail;
		this.passwordHash = passwordHash;
		this.status = AccountStatus.ACTIVE;
		this.mustChangePassword = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

}
