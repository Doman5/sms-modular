package com.domanski.smsmodular.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.domanski.smsmodular.identity.entity.AccountStatus;
import com.domanski.smsmodular.identity.entity.TenantRole;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.tenancy.dto.TenantResponse;

public final class IdentityDtos {

	private IdentityDtos() {
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, boolean mustChangePassword) {
	}

	public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
	}

	public record CreateUserRequest(@NotBlank String email, @NotBlank String displayName, @NotNull UUID roleId) {
	}

	public record UpdateUserRequest(@NotBlank String displayName, @NotNull UUID roleId) {
	}

	public record UserStatusRequest(@NotNull AccountStatus status) {
	}

	public record UserResponse(UUID id, String email, String displayName, UUID roleId, AccountStatus status,
			boolean mustChangePassword, Instant createdAt) {
		public static UserResponse from(UserAccount user) {
			return new UserResponse(user.getId(), user.getNormalizedEmail(), user.getDisplayName(), user.getRoleId(),
					user.getStatus(), user.isMustChangePassword(), user.getCreatedAt());
		}
	}

	public record CreatedUserResponse(UserResponse user, String temporaryPassword) {
	}

	public record TemporaryPasswordResponse(String temporaryPassword) {
	}

	public record CreateRoleRequest(@NotBlank String code, @NotBlank String name, @NotNull Set<String> permissions) {
	}

	public record UpdateRoleRequest(@NotBlank String name, @NotNull Set<String> permissions) {
	}

	public record RoleResponse(UUID id, String code, String name, Set<String> permissions) {
		public static RoleResponse from(TenantRole role, Set<String> permissions) {
			return new RoleResponse(role.getId(), role.getCode(), role.getName(), permissions);
		}
	}

	public record ContextResponse(UserResponse user, TenantResponse tenant, Set<String> permissions,
			List<String> capabilities, List<String> usage) {
	}

	public record PlatformContextResponse(UUID id, String email, Set<String> permissions,
			boolean mustChangePassword) {
	}

	public record ProvisionTenantRequest(@NotBlank String slug, @NotBlank String name,
			@NotBlank String timeZone, @NotBlank String locale, @NotBlank String adminEmail,
			@NotBlank String adminDisplayName) {
	}

	public record ProvisionTenantResponse(TenantResponse tenant, UserResponse firstAdmin, String temporaryPassword) {
	}
}
