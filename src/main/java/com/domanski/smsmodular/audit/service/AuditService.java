package com.domanski.smsmodular.audit.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.domanski.smsmodular.audit.api.AuditActorType;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.dto.AuditEntryResponse;
import com.domanski.smsmodular.audit.dto.AuditFilter;
import com.domanski.smsmodular.audit.entity.AuditEntry;
import com.domanski.smsmodular.audit.repository.AuditRepository;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.common.api.PageResponse;

@Service
@RequiredArgsConstructor
public class AuditService {

	private final AuditRepository entries;
	private final AuditMetadataPolicy metadataPolicy;
	private final Clock clock;

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(AuditCommand command) {
		if (command == null || command.context() == null || command.result() == null
				|| command.targetId() == null || !validCode(command.module())
				|| !validCode(command.action()) || !validCode(command.targetType())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_COMMAND_INVALID", "Audit command is invalid");
		}
		AuditCallContext context = command.context();
		if (context.actorType() == null || context.correlationId() == null
				|| !context.correlationId().matches("[A-Za-z0-9._-]{1,100}")
				|| (context.actorType() == AuditActorType.SYSTEM) != (context.actorId() == null)
				|| (context.actorType() == AuditActorType.TENANT_USER && command.tenantId() == null)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_CONTEXT_INVALID", "Audit context is invalid");
		}
		entries.save(new AuditEntry(UUID.randomUUID(), command.tenantId(), context.actorType(),
				context.actorId(), command.module(), command.action(), command.targetType(),
				command.targetId(), command.result(), clock.instant(), context.correlationId(),
				metadataPolicy.sanitize(command.metadata())));
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditEntryResponse> listTenant(UUID tenantId, AuditFilter filter, Pageable pageable) {
		if (tenantId == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Tenant principal is required");
		}
		return list(tenantId, false, filter, pageable);
	}

	@Transactional(readOnly = true)
	public PageResponse<AuditEntryResponse> listPlatform(UUID tenantId, boolean global, AuditFilter filter,
			Pageable pageable) {
		if ((tenantId == null) != global) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_SCOPE_INVALID",
					"Choose exactly one audit scope");
		}
		return list(tenantId, global, filter, pageable);
	}

	private PageResponse<AuditEntryResponse> list(UUID tenantId, boolean global, AuditFilter filter,
			Pageable pageable) {
		AuditFilter effective = filter == null ? new AuditFilter(null, null, null, null, null, null, null) : filter;
		Instant to = effective.to() == null ? clock.instant().plusSeconds(1) : effective.to();
		Instant from = effective.from() == null ? to.minus(30, ChronoUnit.DAYS) : effective.from();
		if (!to.isAfter(from) || (effective.module() != null && !validCode(effective.module()))
				|| (effective.action() != null && !validCode(effective.action()))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_FILTER_INVALID", "Audit filters are invalid");
		}
		Specification<AuditEntry> specification = (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(global ? builder.isNull(root.get("tenantId")) : builder.equal(root.get("tenantId"), tenantId));
			predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
			predicates.add(builder.lessThan(root.get("occurredAt"), to));
			if (effective.actorId() != null) predicates.add(builder.equal(root.get("actorId"), effective.actorId()));
			if (effective.module() != null) predicates.add(builder.equal(root.get("module"), effective.module()));
			if (effective.action() != null) predicates.add(builder.equal(root.get("action"), effective.action()));
			if (effective.result() != null) predicates.add(builder.equal(root.get("result"), effective.result()));
			if (effective.targetId() != null) predicates.add(builder.equal(root.get("targetId"), effective.targetId()));
			return builder.and(predicates.toArray(Predicate[]::new));
		};
		Pageable fixed = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
				Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
		return PageResponse.from(entries.findAll(specification, fixed).map(AuditEntryResponse::from));
	}

	private boolean validCode(String value) {
		return value != null && value.matches("[A-Z][A-Z0-9_]{0,63}");
	}
}
