package com.domanski.smsmodular.identity.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.identity.entity.AccountStatus;
import com.domanski.smsmodular.identity.entity.PlatformAccount;
import com.domanski.smsmodular.identity.entity.UserAccount;
import com.domanski.smsmodular.identity.repository.PlatformAccountRepository;
import com.domanski.smsmodular.identity.repository.UserAccountRepository;
import com.domanski.smsmodular.identity.service.PermissionCatalog;
import com.domanski.smsmodular.identity.service.RoleService;
import com.domanski.smsmodular.tenancy.dto.TenantResponse;
import com.domanski.smsmodular.tenancy.api.TenantStatus;
import com.domanski.smsmodular.tenancy.service.TenantService;

@Component
@RequiredArgsConstructor
public class IdentityJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserAccountRepository users;
	private final PlatformAccountRepository platformAccounts;
	private final RoleService roles;
	private final TenantService tenants;

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		try {
			UUID id = UUID.fromString(jwt.getSubject());
			Number version = jwt.getClaim("session_version");
			if (version == null) {
				throw new BadCredentialsException("Invalid session");
			}
			if ("platform".equals(jwt.getClaimAsString("kind"))) {
				PlatformAccount account = platformAccounts.findById(id).orElseThrow(() -> new BadCredentialsException("Invalid account"));
				if (account.getStatus() != AccountStatus.ACTIVE || account.getSessionVersion() != version.longValue()) {
					throw new BadCredentialsException("Invalid session");
				}
				List<SimpleGrantedAuthority> authorities = new ArrayList<>();
				authorities.add(new SimpleGrantedAuthority("PLATFORM_USER"));
				if (!account.isMustChangePassword()) {
					authorities.add(new SimpleGrantedAuthority("PLATFORM_ACCESS"));
					authorities.add(new SimpleGrantedAuthority(PermissionCatalog.PLATFORM_TENANT_READ));
					authorities.add(new SimpleGrantedAuthority(PermissionCatalog.PLATFORM_TENANT_MANAGE));
					authorities.add(new SimpleGrantedAuthority(PermissionCatalog.PLATFORM_AUDIT_READ));
				}
				return new UsernamePasswordAuthenticationToken(
						new CurrentPrincipal(id, null, true, account.isMustChangePassword()), jwt, authorities);
			}
			if (!"tenant".equals(jwt.getClaimAsString("kind"))) {
				throw new BadCredentialsException("Invalid principal kind");
			}
			UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
			UserAccount user = users.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new BadCredentialsException("Invalid account"));
			TenantResponse tenant = tenants.get(tenantId);
			if (user.getStatus() != AccountStatus.ACTIVE || user.getSessionVersion() != version.longValue()
					|| tenant.status() != TenantStatus.ACTIVE) {
				throw new BadCredentialsException("Invalid session");
			}
			List<SimpleGrantedAuthority> authorities = new ArrayList<>();
			authorities.add(new SimpleGrantedAuthority("TENANT_USER"));
			if (!user.isMustChangePassword()) {
				authorities.add(new SimpleGrantedAuthority("TENANT_ACCESS"));
				Set<String> permissions = roles.permissions(tenantId, user.getRoleId());
				permissions.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
			}
			return new UsernamePasswordAuthenticationToken(
					new CurrentPrincipal(id, tenantId, false, user.isMustChangePassword()), jwt, authorities);
		} catch (IllegalArgumentException | com.domanski.smsmodular.common.api.ApiException exception) {
			throw new BadCredentialsException("Invalid session", exception);
		}
	}
}
