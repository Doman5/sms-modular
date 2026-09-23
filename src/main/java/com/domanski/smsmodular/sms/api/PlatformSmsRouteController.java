package com.domanski.smsmodular.sms.api;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.sms.dto.SmsDtos.CreateRouteRequest;
import com.domanski.smsmodular.sms.dto.SmsDtos.RouteResponse;
import com.domanski.smsmodular.sms.service.SmsRouteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/platform/v1/sms/routes")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PlatformSmsRouteController {
	private final SmsRouteService routes;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_READ')")
	public List<RouteResponse> list() {
		return routes.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public RouteResponse create(@Valid @RequestBody CreateRouteRequest request, HttpServletRequest servletRequest) {
		return routes.create(request, AuditCallContext.platformUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasAuthority('PLATFORM_TENANT_MANAGE')")
	public RouteResponse deactivate(@PathVariable UUID id, HttpServletRequest servletRequest) {
		return routes.deactivate(id, AuditCallContext.platformUser(current.principal().id(), servletRequest));
	}
}
