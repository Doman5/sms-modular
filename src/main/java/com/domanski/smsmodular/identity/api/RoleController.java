package com.domanski.smsmodular.identity.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.domanski.smsmodular.audit.api.AuditCallContext;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.identity.dto.IdentityDtos.CreateRoleRequest;
import com.domanski.smsmodular.identity.dto.IdentityDtos.RoleResponse;
import com.domanski.smsmodular.identity.dto.IdentityDtos.UpdateRoleRequest;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.identity.service.RoleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

	private final RoleService roles;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAnyAuthority('ROLE_READ', 'USER_MANAGE')")
	public List<RoleResponse> list() {
		return roles.list(current.tenantId());
	}

	@PostMapping
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	public RoleResponse create(@Valid @RequestBody CreateRoleRequest request, HttpServletRequest servletRequest) {
		return roles.create(current.tenantId(), request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request,
			HttpServletRequest servletRequest) {
		return roles.update(current.tenantId(), id, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ROLE_MANAGE')")
	public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest servletRequest) {
		roles.delete(current.tenantId(), id,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
		return ResponseEntity.noContent().build();
	}
}
