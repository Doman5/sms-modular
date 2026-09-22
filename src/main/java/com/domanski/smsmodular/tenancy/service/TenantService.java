package com.domanski.smsmodular.tenancy.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.tenancy.dto.CreateTenantRequest;
import com.domanski.smsmodular.tenancy.dto.TenantResponse;
import com.domanski.smsmodular.tenancy.dto.UpdateTenantRequest;
import com.domanski.smsmodular.tenancy.entity.Tenant;
import com.domanski.smsmodular.tenancy.api.TenantStatus;
import com.domanski.smsmodular.tenancy.repository.TenantRepository;

@Service
@RequiredArgsConstructor
public class TenantService {

	private static final String SLUG_PATTERN = "[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?";

	private final TenantRepository tenantRepository;
	private final Clock clock;
	private final AuditService audit;

	@Transactional
	public TenantResponse create(CreateTenantRequest request) {
		if (request == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Tenant data is required");
		}
		String slug = request.slug() == null ? null : request.slug().trim();
		String name = request.name() == null ? null : request.name().trim();
		validateSlug(slug);
		validateName(name);
		String timeZone = validateTimeZone(request.timeZone());
		String locale = validateLocale(request.locale());
		if (tenantRepository.existsBySlug(slug)) {
			throw new ApiException(HttpStatus.CONFLICT, "TENANT_SLUG_CONFLICT", "Tenant slug is already in use");
		}
		Instant now = clock.instant();
		Tenant tenant = new Tenant(UUID.randomUUID(), slug, name, TenantStatus.ACTIVE, timeZone, locale, now, now);
		try {
			return TenantResponse.from(tenantRepository.saveAndFlush(tenant));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "TENANT_SLUG_CONFLICT", "Tenant slug is already in use");
		}
	}

	@Transactional(readOnly = true)
	public TenantResponse get(UUID tenantId) {
		return TenantResponse.from(requireTenant(tenantId));
	}

	@Transactional
	public void lockForAccessChange(UUID tenantId) {
		tenantRepository.findLockedById(tenantId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant was not found"));
	}

	@Transactional(readOnly = true)
	public TenantResponse requireActiveBySlug(String slug) {
		Tenant tenant = tenantRepository.findBySlug(slug)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant was not found"));
		if (tenant.getStatus() != TenantStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_NOT_ACTIVE", "Tenant is not active");
		}
		return TenantResponse.from(tenant);
	}

	@Transactional(readOnly = true)
	public PageResponse<TenantResponse> list(TenantStatus status, Pageable pageable) {
		Page<Tenant> tenants = status == null
				? tenantRepository.findAll(pageable)
				: tenantRepository.findAllByStatus(status, pageable);
		return PageResponse.from(tenants.map(TenantResponse::from));
	}

	@Transactional
	public TenantResponse update(UUID tenantId, UpdateTenantRequest request, AuditCallContext context) {
		if (request == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "Tenant data is required");
		}
		Tenant tenant = requireTenant(tenantId);
		ensureNotClosed(tenant);
		String name = request.name() == null ? null : request.name().trim();
		validateName(name);
		String timeZone = validateTimeZone(request.timeZone());
		String locale = validateLocale(request.locale());
		List<String> changed = new ArrayList<>();
		if (!tenant.getName().equals(name)) changed.add("NAME");
		if (!tenant.getTimeZone().equals(timeZone)) changed.add("TIME_ZONE");
		if (!tenant.getLocale().equals(locale)) changed.add("LOCALE");
		if (!changed.isEmpty()) {
			tenant.updateDetails(name, timeZone, locale, clock.instant());
			audit.record(AuditCommand.success(tenantId, context, "TENANCY",
					"TENANT_UPDATED", "TENANT", tenantId, Map.of("changedFields", changed)));
		}
		return TenantResponse.from(tenant);
	}

	@Transactional
	public TenantResponse suspend(UUID tenantId, AuditCallContext context) {
		Tenant tenant = requireTenant(tenantId);
		ensureNotClosed(tenant);
		if (tenant.getStatus() == TenantStatus.ACTIVE) {
			tenant.setStatus(TenantStatus.SUSPENDED, clock.instant());
			audit.record(AuditCommand.success(tenantId, context, "TENANCY", "TENANT_SUSPENDED",
					"TENANT", tenantId, Map.of("fromStatus", "ACTIVE", "toStatus", "SUSPENDED")));
		}
		return TenantResponse.from(tenant);
	}

	@Transactional
	public TenantResponse activate(UUID tenantId, AuditCallContext context) {
		Tenant tenant = requireTenant(tenantId);
		ensureNotClosed(tenant);
		if (tenant.getStatus() == TenantStatus.SUSPENDED) {
			tenant.setStatus(TenantStatus.ACTIVE, clock.instant());
			audit.record(AuditCommand.success(tenantId, context, "TENANCY", "TENANT_ACTIVATED",
					"TENANT", tenantId, Map.of("fromStatus", "SUSPENDED", "toStatus", "ACTIVE")));
		}
		return TenantResponse.from(tenant);
	}

	@Transactional
	public TenantResponse close(UUID tenantId, AuditCallContext context) {
		Tenant tenant = requireTenant(tenantId);
		if (tenant.getStatus() != TenantStatus.CLOSED) {
			String previous = tenant.getStatus().name();
			tenant.close(clock.instant());
			audit.record(AuditCommand.success(tenantId, context, "TENANCY", "TENANT_CLOSED",
					"TENANT", tenantId, Map.of("fromStatus", previous, "toStatus", "CLOSED")));
		}
		return TenantResponse.from(tenant);
	}

	private Tenant requireTenant(UUID tenantId) {
		return tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant was not found"));
	}

	private void validateSlug(String slug) {
		if (slug == null || !slug.matches(SLUG_PATTERN)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_SLUG_INVALID", "Tenant slug is invalid");
		}
	}

	private void validateName(String name) {
		if (name == null || name.isBlank() || name.length() > 160) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_NAME_INVALID", "Tenant name is invalid");
		}
	}

	private String validateTimeZone(String value) {
		try {
			ZoneId zoneId = ZoneId.of(value);
			if (zoneId instanceof ZoneOffset) {
				throw new DateTimeException("Offset is not an IANA time zone");
			}
			return zoneId.getId();
		} catch (DateTimeException | NullPointerException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_TIME_ZONE_INVALID", "Tenant time zone is invalid");
		}
	}

	private String validateLocale(String value) {
		try {
			String languageTag = new Locale.Builder().setLanguageTag(value).build().toLanguageTag();
			if (languageTag.equals("und")) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_LOCALE_INVALID", "Tenant locale is invalid");
			}
			return languageTag;
		} catch (java.util.IllformedLocaleException | NullPointerException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_LOCALE_INVALID", "Tenant locale is invalid");
		}
	}

	private void ensureNotClosed(Tenant tenant) {
		if (tenant.getStatus() == TenantStatus.CLOSED) {
			throw new ApiException(HttpStatus.CONFLICT, "TENANT_CLOSED", "Closed tenant cannot be changed");
		}
	}
}
