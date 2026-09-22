package com.domanski.smsmodular.identity.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.identity.dto.IdentityDtos.LoginResponse;
import com.domanski.smsmodular.identity.entity.PlatformAccount;
import com.domanski.smsmodular.identity.entity.UserAccount;

@Service
@RequiredArgsConstructor
public class TokenService {

	public static final String ISSUER = "sms-modular";
	public static final String AUDIENCE = "sms-modular-api";
	private final JwtEncoder encoder;
	private final Clock clock;

	public LoginResponse issue(UserAccount user) {
		return issue(user.getId(), user.getTenantId(), "tenant", user.getSessionVersion(), user.isMustChangePassword());
	}

	public LoginResponse issue(PlatformAccount account) {
		return issue(account.getId(), null, "platform", account.getSessionVersion(), account.isMustChangePassword());
	}

	private LoginResponse issue(UUID id, UUID tenantId, String kind, long version, boolean mustChange) {
		Instant now = clock.instant();
		Instant expires = now.plus(8, ChronoUnit.HOURS);
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(ISSUER).audience(java.util.List.of(AUDIENCE))
				.subject(id.toString()).issuedAt(now).expiresAt(expires)
				.claim("kind", kind).claim("session_version", version);
		if (tenantId != null) {
			claims.claim("tenant_id", tenantId.toString());
		}
		Jwt jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()));
		return new LoginResponse(jwt.getTokenValue(), "Bearer", expires, mustChange);
	}
}
