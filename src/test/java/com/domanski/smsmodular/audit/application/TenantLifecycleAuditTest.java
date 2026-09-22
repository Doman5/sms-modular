package com.domanski.smsmodular.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.domanski.smsmodular.audit.api.contract.ActorRef;
import com.domanski.smsmodular.audit.api.contract.AuditCommand;
import com.domanski.smsmodular.audit.api.contract.AuditPort;
import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleEvent;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleEventPublisher;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleService;
import com.domanski.smsmodular.tenancy.application.TenantSettingsCommand;
import com.domanski.smsmodular.tenancy.domain.Tenant;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantLifecycleAuditTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	void platformTenantLifecycleChangesAreAuditedWithTheConcreteTenantId() {
		TenantRepository store = new TenantRepository();
		RecordingAuditPort auditPort = new RecordingAuditPort();
		TenantLifecycleService service = new TenantLifecycleService(
			store, event -> { }, auditPort, CLOCK
		);

		TenantId tenantId = service.create("audit-lifecycle", "Lifecycle", "UTC", "en").id();

		assertThat(auditPort.commands).singleElement().satisfies(command -> {
			assertThat(command.tenantId()).isEqualTo(tenantId);
			assertThat(command.platformOperation()).isFalse();
			assertThat(command.actor()).isNotNull();
			assertThat(command.actor().type()).isEqualTo(ActorRef.PLATFORM);
			assertThat(command.action()).isEqualTo(TenantLifecycleEvent.Action.CREATED.name());
		});
	}

	@Test
	void tenantSettingsChangeUsesVerifiedTenantContextAndAuditPort() {
		TenantRepository store = new TenantRepository();
		Tenant tenant = Tenant.create("audit-self-service", "Before", "UTC", "en", CLOCK);
		store.save(tenant);
		RecordingAuditPort auditPort = new RecordingAuditPort();
		TenantLifecycleService service = new TenantLifecycleService(
			store, event -> { }, auditPort, CLOCK
		);

		TenantContext.runWith(tenant.id(), () -> service.updateCurrent(
			new TenantSettingsCommand("After", null, null)
		));

		assertThat(auditPort.commands).singleElement().satisfies(command -> {
			assertThat(command.tenantId()).isEqualTo(tenant.id());
			assertThat(command.platformOperation()).isFalse();
			assertThat(command.action()).isEqualTo("SETTINGS_UPDATED");
		});
		assertThat(store.findById(tenant.id().value()).orElseThrow().name()).isEqualTo("After");
	}

	private static final class RecordingAuditPort implements AuditPort {
		private final List<AuditCommand> commands = new ArrayList<>();

		@Override
		public void record(AuditCommand command) {
			commands.add(command);
		}
	}
}
