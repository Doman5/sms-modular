package com.domanski.smsmodular.sms.api;

import java.time.Instant;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.identity.security.CurrentIdentity;
import com.domanski.smsmodular.sms.dto.SmsDtos.ResolveRequest;
import com.domanski.smsmodular.sms.dto.SmsDtos.SmsResponse;
import com.domanski.smsmodular.sms.service.SmsProcessingService;
import com.domanski.smsmodular.sms.service.SmsQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/sms")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SmsController {
	private final SmsQueryService query;
	private final SmsProcessingService processing;
	private final CurrentIdentity current;

	@GetMapping
	@PreAuthorize("hasAuthority('SMS_READ')")
	public PageResponse<SmsResponse> list(@RequestParam Instant from, @RequestParam Instant to,
			@RequestParam(required = false) String status, @RequestParam(required = false) UUID employeeId,
			@RequestParam(defaultValue = "false") boolean reviewOnly, Pageable pageable) {
		return query.list(current.tenantId(), from, to, status, employeeId, reviewOnly, pageable);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('SMS_READ')")
	public SmsResponse get(@PathVariable UUID id) {
		return query.get(current.tenantId(), id);
	}

	@PostMapping("/{id}/resolve")
	@PreAuthorize("hasAuthority('SMS_REVIEW')")
	public SmsResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request,
			HttpServletRequest servletRequest) {
		return processing.resolve(current.tenantId(), id, request,
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	@PostMapping("/{id}/reparse")
	@PreAuthorize("hasAuthority('SMS_REVIEW')")
	public SmsResponse reparse(@PathVariable UUID id, @Valid @RequestBody VersionRequest request,
			HttpServletRequest servletRequest) {
		return processing.reparse(current.tenantId(), id, request.version(),
				AuditCallContext.tenantUser(current.principal().id(), servletRequest));
	}

	public record VersionRequest(@NotNull @PositiveOrZero Long version) {
	}
}
