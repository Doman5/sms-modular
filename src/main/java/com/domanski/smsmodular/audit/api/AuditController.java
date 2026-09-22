package com.domanski.smsmodular.audit.api;

import com.domanski.smsmodular.audit.application.AuditFilter;
import com.domanski.smsmodular.audit.application.AuditQueryService;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
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
@RequestMapping("/api/v1/audit-logs")
@Validated
public class AuditController {

	private final AuditQueryService service;

	public AuditController(AuditQueryService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public PageResponse<AuditLogResponse> list(
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
		TenantContext.require();
		return PageResponse.from(service.findForCurrentTenant(
			new AuditFilter(null, from, to, actorType, actorId, module, action, subjectType, subjectId),
			pageable(page, size)
		).map(AuditLogResponse::from));
	}

	@GetMapping("/{auditId}")
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public AuditLogResponse get(@PathVariable UUID auditId) {
		TenantContext.require();
		return AuditLogResponse.from(service.getForCurrentTenant(auditId));
	}

	private Pageable pageable(int page, int size) {
		return PageRequest.of(page, size, Sort.by(
			Sort.Order.desc("occurredAt"),
			Sort.Order.desc("id")
		));
	}
}
