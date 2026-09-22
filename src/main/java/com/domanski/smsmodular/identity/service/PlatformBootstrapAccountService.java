package com.domanski.smsmodular.identity.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.identity.entity.PlatformAccount;
import com.domanski.smsmodular.identity.repository.PlatformAccountRepository;

@Service
@RequiredArgsConstructor
public class PlatformBootstrapAccountService {

	private final PlatformAccountRepository accounts;
	private final IdentityPolicy policy;
	private final PasswordEncoder passwords;
	private final Clock clock;
	private final AuditService audit;

	@Value("${app.security.bootstrap-platform-email:}")
	private String email;

	@Value("${app.security.bootstrap-platform-password:}")
	private String password;

	@Transactional
	public void bootstrap() {
		if (accounts.count() != 0) return;
		if (email.isBlank() || password.isBlank()) {
			throw new IllegalStateException("BOOTSTRAP_PLATFORM_EMAIL and BOOTSTRAP_PLATFORM_PASSWORD are required for first startup");
		}
		policy.password(password);
		PlatformAccount account = accounts.save(new PlatformAccount(UUID.randomUUID(), policy.email(email),
				passwords.encode(password), clock.instant()));
		audit.record(AuditCommand.success(null, AuditCallContext.system("platform-bootstrap"),
				"IDENTITY", "PLATFORM_BOOTSTRAPPED", "PLATFORM_USER", account.getId(), Map.of()));
	}
}
