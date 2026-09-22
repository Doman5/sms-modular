package com.domanski.smsmodular.audit.api;

import com.domanski.smsmodular.audit.application.AuditFilter;
import com.domanski.smsmodular.audit.application.AuditQueryService;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/platform/v1/audit-logs")
@Validated
public class PlatformAuditController {

	private final AuditQueryService service;

	public PlatformAuditController(AuditQueryService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PLATFORM_AUDIT_READ')")
	public PageResponse<AuditLogResponse> list(
		@RequestParam UUID tenantId,
		@DateTimeFormat(iso = ISO.DATE_TIME) @RequestParam(required = false) Instant from,
		@DateTimeFormat(iso = ISO.DATE_TIME) @RequestParam(required = false) Instant to,
		@RequestParam(required = false) String actorType,
		@RequestParam(required = false) UUID actorId,
		@RequestParam(required = false) String module,
		@RequestParam(required = false) String action,
		@RequestParam(required = false) String subjectType,
		@RequestParam(required = false) UUID subjectId,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
	) {
		return PageResponse.from(service.findForPlatform(
			TenantId.of(tenantId),
			new AuditFilter(null, from, to, actorType, actorId, module, action, subjectType, subjectId),
			pageable(page, size)
		).map(AuditLogResponse::from));
	}

	@GetMapping("/{auditId}")
	@PreAuthorize("hasAuthority('PLATFORM_AUDIT_READ')")
	public AuditLogResponse get(@RequestParam UUID tenantId, @PathVariable UUID auditId) {
		return AuditLogResponse.from(service.getForPlatform(TenantId.of(tenantId), auditId));
	}

	private Pageable pageable(int page, int size) {
		return PageRequest.of(page, size, Sort.by(
			Sort.Order.desc("occurredAt"),
			Sort.Order.desc("id")
		));
	}
}
