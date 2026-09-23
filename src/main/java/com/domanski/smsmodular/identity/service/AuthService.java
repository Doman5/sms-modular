package com.domanski.smsmodular.identity.service;

import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.audit.api.AuditActorType;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.api.AuditResult;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ContextResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.LoginRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.LoginResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.PlatformContextResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UserResponse;
import com.domanski.smsmodular.identity.entity.AccountStatus;
import com.domanski.smsmodular.identity.entity.PlatformAccount;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.repository.PlatformAccountRepository;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.identity.security.TokenService;
import com.domanski.smsmodular.tenancy.api.TenantStatus;
import com.domanski.smsmodular.tenancy.service.TenantService;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.usage.service.UsageService;
import com.domanski.smsmodular.employee.service.EmployeeService;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final String dummyHash = new BCryptPasswordEncoder(12).encode("not-a-real-account-password");
	private final UserAccountRepository users;
	private final PlatformAccountRepository platformAccounts;
	private final PasswordEncoder passwords;
	private final IdentityPolicy policy;
	private final TokenService tokens;
	private final TenantService tenants;
	private final RoleService roles;
	private final Clock clock;
	private final AuditService audit;
	private final EntitlementService entitlements;
	private final UsageService usage;
	private final EmployeeService employees;

	@Transactional(noRollbackFor = ApiException.class)
	public LoginResponse login(LoginRequest request, String correlationId) {
		if (passwordTooLong(request.password())) {
			throw invalidCredentials();
		}
		String email = normalizedLoginEmail(request.email());
		UserAccount user = users.findLockedByEmail(email).orElse(null);
		if (user == null) {
			passwords.matches(request.password(), dummyHash);
			throw invalidCredentials();
		}
		if (locked(user.getLockedUntil())) {
			throw invalidCredentials();
		}
		if (!passwords.matches(request.password(), user.getPasswordHash())) {
			failed(user);
			recordLogin(user.getTenantId(), AuditActorType.TENANT_USER, user.getId(), AuditResult.DENIED, correlationId);
			throw invalidCredentials();
		}
		if (user.getStatus() != AccountStatus.ACTIVE || tenants.get(user.getTenantId()).status() != TenantStatus.ACTIVE) {
			recordLogin(user.getTenantId(), AuditActorType.TENANT_USER, user.getId(), AuditResult.DENIED, correlationId);
			throw invalidCredentials();
		}
		clearFailures(user);
		recordLogin(user.getTenantId(), AuditActorType.TENANT_USER, user.getId(), AuditResult.SUCCESS, correlationId);
		return tokens.issue(user);
	}

	@Transactional(noRollbackFor = ApiException.class)
	public LoginResponse platformLogin(LoginRequest request, String correlationId) {
		if (passwordTooLong(request.password())) {
			throw invalidCredentials();
		}
		String email = normalizedLoginEmail(request.email());
		PlatformAccount account = platformAccounts.findLockedByEmail(email).orElse(null);
		if (account == null) {
			passwords.matches(request.password(), dummyHash);
			throw invalidCredentials();
		}
		if (locked(account.getLockedUntil())) {
			throw invalidCredentials();
		}
		if (!passwords.matches(request.password(), account.getPasswordHash())) {
			failed(account);
			recordLogin(null, AuditActorType.PLATFORM_USER, account.getId(), AuditResult.DENIED, correlationId);
			throw invalidCredentials();
		}
		if (account.getStatus() != AccountStatus.ACTIVE) {
			recordLogin(null, AuditActorType.PLATFORM_USER, account.getId(), AuditResult.DENIED, correlationId);
			throw invalidCredentials();
		}
		clearFailures(account);
		recordLogin(null, AuditActorType.PLATFORM_USER, account.getId(), AuditResult.SUCCESS, correlationId);
		return tokens.issue(account);
	}

	@Transactional
	public void changePassword(UUID tenantId, UUID userId, ChangePasswordRequest request, AuditCallContext context) {
		UserAccount user = users.findLockedByTenantIdAndId(tenantId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Authentication failed"));
		if (passwordTooLong(request.currentPassword()) || !passwords.matches(request.currentPassword(), user.getPasswordHash())) {
			throw invalidCredentials();
		}
		policy.password(request.newPassword());
		if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "New password must differ from current password");
		}
		user.setPasswordHash(passwords.encode(request.newPassword()));
		user.setMustChangePassword(false);
		user.setSessionVersion(user.getSessionVersion() + 1);
		user.setUpdatedAt(clock.instant());
		audit.record(AuditCommand.success(tenantId, context, "IDENTITY", "PASSWORD_CHANGED",
				"USER", userId, Map.of()));
	}

	@Transactional
	public void changePlatformPassword(UUID id, ChangePasswordRequest request, AuditCallContext context) {
		PlatformAccount account = platformAccounts.findLockedById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Authentication failed"));
		if (passwordTooLong(request.currentPassword()) || !passwords.matches(request.currentPassword(), account.getPasswordHash())) {
			throw invalidCredentials();
		}
		policy.password(request.newPassword());
		if (passwords.matches(request.newPassword(), account.getPasswordHash())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "New password must differ from current password");
		}
		account.setPasswordHash(passwords.encode(request.newPassword()));
		account.setMustChangePassword(false);
		account.setSessionVersion(account.getSessionVersion() + 1);
		account.setUpdatedAt(clock.instant());
		audit.record(AuditCommand.success(null, context, "IDENTITY", "PLATFORM_PASSWORD_CHANGED",
				"PLATFORM_USER", id, Map.of()));
	}

	@Transactional(readOnly = true)
	public ContextResponse context(UUID tenantId, UUID id) {
		UserAccount user = users.findByTenantIdAndId(tenantId, id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
		return new ContextResponse(UserResponse.from(user), tenants.get(tenantId),
				roles.permissions(tenantId, user.getRoleId()),
				entitlements.snapshot(tenantId).capabilities().stream().sorted().toList(),
				java.util.List.of(usage.activeUsers(tenantId,
						users.countByTenantIdAndStatus(tenantId, AccountStatus.ACTIVE)),
						usage.activeEmployees(tenantId, employees.activeCount(tenantId))));
	}

	@Transactional(readOnly = true)
	public PlatformContextResponse platformContext(UUID id) {
		PlatformAccount account = platformAccounts.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found"));
		return new PlatformContextResponse(id, account.getNormalizedEmail(),
				java.util.Set.of(PermissionCatalog.PLATFORM_TENANT_READ, PermissionCatalog.PLATFORM_TENANT_MANAGE,
						PermissionCatalog.PLATFORM_AUDIT_READ,
						PermissionCatalog.PLATFORM_SUBSCRIPTION_READ,
						PermissionCatalog.PLATFORM_SUBSCRIPTION_MANAGE),
				account.isMustChangePassword());
	}

	private String normalizedLoginEmail(String value) {
		try {
			return policy.email(value);
		} catch (ApiException exception) {
			throw invalidCredentials();
		}
	}

	private boolean passwordTooLong(String password) {
		return password == null || password.getBytes(StandardCharsets.UTF_8).length > 72;
	}

	private boolean locked(Instant until) {
		return until != null && clock.instant().isBefore(until);
	}

	private void failed(UserAccount user) {
		Instant now = clock.instant();
		int attempts = user.getFirstFailedAt() == null || now.isAfter(user.getFirstFailedAt().plus(15, ChronoUnit.MINUTES))
				? 1 : user.getFailedAttempts() + 1;
		user.setFirstFailedAt(attempts == 1 ? now : user.getFirstFailedAt());
		user.setFailedAttempts(attempts);
		if (attempts >= 5) user.setLockedUntil(now.plus(15, ChronoUnit.MINUTES));
	}

	private void failed(PlatformAccount account) {
		Instant now = clock.instant();
		int attempts = account.getFirstFailedAt() == null || now.isAfter(account.getFirstFailedAt().plus(15, ChronoUnit.MINUTES))
				? 1 : account.getFailedAttempts() + 1;
		account.setFirstFailedAt(attempts == 1 ? now : account.getFirstFailedAt());
		account.setFailedAttempts(attempts);
		if (attempts >= 5) account.setLockedUntil(now.plus(15, ChronoUnit.MINUTES));
	}

	private void clearFailures(UserAccount user) {
		user.setFailedAttempts(0);
		user.setFirstFailedAt(null);
		user.setLockedUntil(null);
	}

	private void clearFailures(PlatformAccount account) {
		account.setFailedAttempts(0);
		account.setFirstFailedAt(null);
		account.setLockedUntil(null);
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID", "Authentication failed");
	}

	private void recordLogin(UUID tenantId, AuditActorType actorType, UUID actorId,
			AuditResult result, String correlationId) {
		audit.record(new AuditCommand(tenantId, new AuditCallContext(actorType, actorId, correlationId),
				"IDENTITY", "LOGIN", actorType == AuditActorType.PLATFORM_USER ? "PLATFORM_USER" : "USER",
				actorId, result, Map.of()));
	}
}
