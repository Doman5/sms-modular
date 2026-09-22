package com.domanski.smsmodular.integrationruntime.api;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.integrationruntime.application.OutboxAdminService;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;


@RestController
@Validated
@RequestMapping({"/api/platform/v1/integration-runtime", "/api/platform/v1/integration"})
public class IntegrationRuntimeController {

	private final OutboxAdminService adminService;

	public IntegrationRuntimeController(OutboxAdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/dead-letters")
	@PreAuthorize("hasAuthority('PLATFORM_INTEGRATION_READ')")
	public PageResponse<DeadLetterResponse> listDeadLetters(
		@RequestParam UUID tenantId,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
	) {
		PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
		return PageResponse.from(adminService.findDeadLetters(TenantId.of(tenantId), request).map(DeadLetterResponse::from));
	}

	@GetMapping("/dead-letters/{id}")
	@PreAuthorize("hasAuthority('PLATFORM_INTEGRATION_READ')")
	public DeadLetterResponse getDeadLetter(@PathVariable UUID id, @RequestParam UUID tenantId) {
		return DeadLetterResponse.from(adminService.findDeadLetter(TenantId.of(tenantId), id));
	}

	@PostMapping("/dead-letters/{id}/retry")
	@PreAuthorize("hasAuthority('PLATFORM_INTEGRATION_RETRY')")
	public DeadLetterResponse retryDeadLetter(
		@PathVariable UUID id,
		@RequestParam UUID tenantId,
		Authentication authentication
	) {
		return DeadLetterResponse.from(adminService.retryDeadLetter(
			TenantId.of(tenantId), id, ActorRef.platform(actorId(authentication))
		));
	}

	private UUID actorId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			return null;
		}
		try {
			return UUID.fromString(authentication.getName());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
