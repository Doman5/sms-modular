package com.domanski.smsmodular.audit.application;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.audit.api.contract.AuditCommand;
import com.domanski.smsmodular.audit.api.contract.AuditPort;
import com.domanski.smsmodular.audit.domain.AuditEntry;
import com.domanski.smsmodular.audit.infrastructure.persistence.AuditRepository;
import com.domanski.smsmodular.common.context.CorrelationContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;


@Service
public class AuditRecorder implements AuditPort {

	private final AuditRepository repository;
	private final Clock clock;
	private final JdbcTemplate jdbcTemplate;

	public AuditRecorder(AuditRepository repository, Clock clock, ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
		this.repository = repository;
		this.clock = clock;
		this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
	}

	@Override
	public void record(AuditCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("Audit command is required");
		}
		TenantId contextTenant = TenantContext.current().orElse(null);
		if (command.platformOperation()) {
			if (contextTenant != null) {
				throw new IllegalArgumentException("A platform audit operation cannot run in a tenant context");
			}
			setPlatformContext();
		} else {
			TenantId requiredTenant = TenantContext.require();
			if (!requiredTenant.equals(command.tenantId())) {
				throw new IllegalArgumentException("Audit command tenant does not match the verified tenant context");
			}
			setTenantContext(requiredTenant);
		}

		ActorRef actor = resolveActor(command.actor(), command.platformOperation());
		String correlationId = command.correlationId() == null
			? CorrelationContext.currentOrGenerate()
			: command.correlationId();
		if (!CorrelationContext.isSafe(correlationId)) {
			throw new IllegalArgumentException("Audit correlation ID has an invalid format");
		}
		Instant occurredAt = command.occurredAt() == null ? clock.instant() : command.occurredAt();
		repository.save(new AuditEntry(
			UUID.randomUUID(),
			command.tenantId(),
			actor,
			command.module(),
			command.action(),
			command.subjectType(),
			command.subjectId(),
			command.outcome(),
			occurredAt,
			correlationId,
			command.metadata()
		));
	}

	private void setTenantContext(TenantId tenantId) {
		if (jdbcTemplate == null) {
			return;
		}
		jdbcTemplate.queryForObject(
			"select set_config('app.tenant_id', ?, true)", String.class, tenantId.value().toString()
		);
		jdbcTemplate.queryForObject("select set_config('app.audit_platform', 'false', true)", String.class);
	}

	private void setPlatformContext() {
		if (jdbcTemplate == null) {
			return;
		}
		jdbcTemplate.queryForObject("select set_config('app.tenant_id', '', true)", String.class);
		jdbcTemplate.queryForObject("select set_config('app.audit_platform', 'true', true)", String.class);
	}

	private ActorRef resolveActor(ActorRef requested, boolean platformOperation) {
		if (requested != null) {
			return requested;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return platformOperation ? ActorRef.platform(null) : ActorRef.system();
		}
		if (platformOperation || hasPlatformAuthority(authentication)) {
			return ActorRef.platform(parseUuid(authentication.getName()));
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof TenantPrincipal tenantPrincipal) {
			return ActorRef.user(parseUuid(tenantPrincipal.subject()));
		}
		if (authentication instanceof JwtAuthenticationToken jwt) {
			return ActorRef.user(parseUuid(jwt.getToken().getSubject()));
		}
		return ActorRef.user(parseUuid(authentication.getName()));
	}

	private boolean hasPlatformAuthority(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(value -> "PLATFORM_OPERATOR".equals(value) || value.startsWith("PLATFORM_"));
	}

	private UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
