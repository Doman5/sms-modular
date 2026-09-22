package com.domanski.smsmodular.identity.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.domanski.smsmodular.audit.api.AuditCallContext;

import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.CreateUserRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.CreatedUserResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.TemporaryPasswordResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UpdateUserRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UserResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UserStatusRequest;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.identity.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

	private final UserService users;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('USER_READ')")
	public PageResponse<UserResponse> list(Pageable pageable) {
		return users.list(current.tenantId(), pageable);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request,
			HttpServletRequest servletRequest) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.create(current.tenantId(),
				request, AuditCallContext.tenantUser(current.principal().id(), servletRequest)));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
			HttpServletRequest servletRequest) {
		return users.update(current.tenantId(), id, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public UserResponse status(@PathVariable UUID id, @Valid @RequestBody UserStatusRequest request,
			HttpServletRequest servletRequest) {
		return users.setStatus(current.tenantId(), id, request.status(),
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/reset-password")
	@PreAuthorize("hasAuthority('USER_MANAGE')")
	public ResponseEntity<TemporaryPasswordResponse> resetPassword(@PathVariable UUID id,
			HttpServletRequest servletRequest) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(users.resetPassword(current.tenantId(), id,
						AuditCallContext.tenantUser(current.principal().id(), servletRequest)));
	}
}
