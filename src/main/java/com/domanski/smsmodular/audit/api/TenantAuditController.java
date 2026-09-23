package com.domanski.smsmodular.audit.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.domanski.smsmodular.audit.dto.AuditEntryResponse;
import com.domanski.smsmodular.audit.dto.AuditFilter;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/audit-logs")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TenantAuditController {

	private final AuditService audit;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public PageResponse<AuditEntryResponse> list(@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String module, @RequestParam(required = false) String action,
			@RequestParam(required = false) AuditResult result, @RequestParam(required = false) UUID targetId,
			@RequestParam(required = false) String search,
			Pageable pageable) {
		return audit.listTenant(current.tenantId(),
				new AuditFilter(from, to, actorId, module, action, result, targetId, search), pageable);
	}
}
