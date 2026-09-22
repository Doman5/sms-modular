package com.domanski.smsmodular.tenancy.application;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.audit.api.contract.AuditCommand;
import com.domanski.smsmodular.audit.api.contract.AuditPort;
import com.domanski.smsmodular.common.api.ApiProblemCode;
import com.domanski.smsmodular.common.domain.ConflictException;
import com.domanski.smsmodular.common.domain.DomainException;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class TenantLifecycleService {

	private final TenantRepository tenantStore;
	private final TenantLifecycleEventPublisher eventPublisher;
	private final AuditPort auditPort;
	private final Clock clock;

	public TenantLifecycleService(
		TenantRepository tenantStore,
		TenantLifecycleEventPublisher eventPublisher,
		AuditPort auditPort,
		Clock clock
	) {
		this.tenantStore = tenantStore;
		this.eventPublisher = eventPublisher;
		this.auditPort = auditPort;
		this.clock = clock;
	}

	@Transactional
	public TenantView create(String slug, String name, String timezone, String locale) {
		Tenant tenant = Tenant.create(slug, name, timezone, locale, clock);
		if (tenantStore.findBySlug(tenant.slug()).isPresent()) {
			throw new ConflictException("A tenant with this slug already exists.");
		}
		Tenant saved = tenantStore.save(tenant);
		recordPlatformAudit(saved, TenantLifecycleEvent.Action.CREATED);
		publish(saved, TenantLifecycleEvent.Action.CREATED);
		return view(saved);
	}

	@Transactional(readOnly = true)
	public Page<TenantView> list(Pageable pageable) {
		return tenantStore.findAll(pageable).map(this::view);
	}

	@Transactional(readOnly = true)
	public TenantView get(TenantId tenantId) {
		return view(require(tenantId));
	}

	@Transactional
	public TenantView update(TenantId tenantId, TenantSettingsCommand command) {
		Tenant tenant = require(tenantId);
		updateSettings(tenant, command);
		Tenant saved = tenantStore.save(tenant);
		recordPlatformAudit(saved, TenantLifecycleEvent.Action.SETTINGS_UPDATED);
		publish(saved, TenantLifecycleEvent.Action.SETTINGS_UPDATED);
		return view(saved);
	}

	@Transactional
	public TenantView suspend(TenantId tenantId) {
		Tenant tenant = require(tenantId);
		tenant.suspend(clock);
		Tenant saved = tenantStore.save(tenant);
		recordPlatformAudit(saved, TenantLifecycleEvent.Action.SUSPENDED);
		publish(saved, TenantLifecycleEvent.Action.SUSPENDED);
		return view(saved);
	}

	@Transactional
	public TenantView activate(TenantId tenantId) {
		Tenant tenant = require(tenantId);
		tenant.activate(clock);
		Tenant saved = tenantStore.save(tenant);
		recordPlatformAudit(saved, TenantLifecycleEvent.Action.ACTIVATED);
		publish(saved, TenantLifecycleEvent.Action.ACTIVATED);
		return view(saved);
	}

	@Transactional
	public TenantView close(TenantId tenantId) {
		Tenant tenant = require(tenantId);
		tenant.close(clock);
		Tenant saved = tenantStore.save(tenant);
		recordPlatformAudit(saved, TenantLifecycleEvent.Action.CLOSED);
		publish(saved, TenantLifecycleEvent.Action.CLOSED);
		return view(saved);
	}

	@TenantScopedTransactional(readOnly = true)
	public TenantView getCurrent() {
		return view(require(TenantContext.require()));
	}

	@TenantScopedTransactional
	public TenantView updateCurrent(TenantSettingsCommand command) {
		Tenant tenant = require(TenantContext.require());
		tenant.requireActiveForCommand();
		updateSettings(tenant, command);
		Tenant saved = tenantStore.save(tenant);
		recordTenantAudit(saved, TenantLifecycleEvent.Action.SETTINGS_UPDATED);
		publish(saved, TenantLifecycleEvent.Action.SETTINGS_UPDATED);
		return view(saved);
	}

	private Tenant require(TenantId tenantId) {
		return tenantStore.findById(tenantId.value())
			.orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
	}

	private void updateSettings(Tenant tenant, TenantSettingsCommand command) {
		if (command == null || !command.hasAnyValue()) {
			throw new DomainException(ApiProblemCode.VALIDATION_ERROR, "At least one tenant setting is required.");
		}
		tenant.updateSettings(
			command.name() == null ? tenant.name() : command.name(),
			command.timezone() == null ? tenant.timezone() : command.timezone(),
			command.locale() == null ? tenant.locale() : command.locale(),
			clock
		);
	}

	private void publish(Tenant tenant, TenantLifecycleEvent.Action action) {
		java.util.UUID eventId = java.util.UUID.randomUUID();
		eventPublisher.publish(new TenantLifecycleEvent(
			tenant.id(),
			action,
			clock.instant(),
			com.domanski.smsmodular.common.context.CorrelationContext.currentOrGenerate(),
			1,
			eventId,
			eventId.toString()
		));
	}

	private void recordPlatformAudit(Tenant tenant, TenantLifecycleEvent.Action action) {
		
		
		
		try (TenantContext.Scope ignored = TenantContext.open(tenant.id())) {
			auditPort.record(AuditCommand.tenant(
				tenant.id(),
				ActorRef.platform(null),
				"TENANCY",
				action.name(),
				"TENANT",
				tenant.id().value(),
				"SUCCESS",
				clock.instant(),
				com.domanski.smsmodular.common.context.CorrelationContext.currentOrGenerate(),
				Map.of("operation", action.name())
			));
		}
	}

	private void recordTenantAudit(Tenant tenant, TenantLifecycleEvent.Action action) {
		auditPort.record(AuditCommand.tenant(
			tenant.id(),
			null,
			"TENANCY",
			action.name(),
			"TENANT",
			tenant.id().value(),
			"SUCCESS",
			clock.instant(),
			com.domanski.smsmodular.common.context.CorrelationContext.currentOrGenerate(),
			Map.of("operation", action.name())
		));
	}

	private TenantView view(Tenant tenant) {
		return new TenantView(
			tenant.id(),
			tenant.slug(),
			tenant.name(),
			tenant.status(),
			tenant.timezone(),
			tenant.locale(),
			tenant.createdAt(),
			tenant.updatedAt(),
			tenant.closedAt()
		);
	}
}
