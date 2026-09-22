package com.domanski.smsmodular.identity.api;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.domanski.smsmodular.audit.api.AuditCallContext;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.ContextResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.LoginRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.LoginResponse;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.identity.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService auth;
	private final CurrentIdentity current;

	@PostMapping("/auth/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(auth.login(request, AuditCallContext.correlationId(servletRequest)));
	}

	@PostMapping("/auth/change-password")
	@SecurityRequirement(name = "bearerAuth")
	public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
			HttpServletRequest servletRequest) {
		auth.changePassword(current.tenantId(), current.principal().id(), request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me/context")
	@SecurityRequirement(name = "bearerAuth")
	public ResponseEntity<ContextResponse> context() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(auth.context(current.tenantId(), current.principal().id()));
	}
}
