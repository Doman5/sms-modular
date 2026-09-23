package com.domanski.smsmodular.sms.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.sms.dto.SmsDtos.CreateRouteRequest;
import com.domanski.smsmodular.sms.dto.SmsDtos.RouteResponse;
import com.domanski.smsmodular.sms.entity.SmsRoute;
import com.domanski.smsmodular.sms.repository.SmsRouteRepository;
import com.domanski.smsmodular.tenancy.api.TenantStatus;
import com.domanski.smsmodular.tenancy.service.TenantService;

@Service
@RequiredArgsConstructor
public class SmsRouteService {
	private final SmsRouteRepository routes;
	private final SmsSettings settings;
	private final TenantService tenants;
	private final AuditService audit;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<RouteResponse> list() {
		return routes.findByActiveTrueOrderByDeviceIdAscIdAsc().stream().map(RouteResponse::from).toList();
	}

	@Transactional
	public RouteResponse create(CreateRouteRequest request, AuditCallContext context) {
		if (request == null || request.tenantId() == null) throw invalid();
		if (settings.deviceId() == null || settings.deviceId().isBlank()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_INTEGRATION_NOT_CONFIGURED",
					"SMS device is not configured");
		}
		if (tenants.get(request.tenantId()).status() != TenantStatus.ACTIVE) {
			throw new ApiException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE", "Tenant is not active");
		}
		String recipient = request.recipient() == null || request.recipient().isBlank()
				? null : normalized(request.recipient());
		Integer sim = request.simNumber();
		if (recipient == null && sim == null || sim != null && sim < 0) throw invalid();
		if (recipient != null && routes.findByDeviceIdAndRecipientAndActiveTrue(settings.deviceId(), recipient).isPresent()
				|| sim != null && routes.findByDeviceIdAndSimNumberAndActiveTrue(settings.deviceId(), sim).isPresent()) {
			throw conflict();
		}
		SmsRoute route = new SmsRoute(request.tenantId(), settings.deviceId(), recipient, sim, clock.instant());
		try {
			routes.saveAndFlush(route);
		} catch (DataIntegrityViolationException exception) {
			throw conflict();
		}
		audit.record(AuditCommand.success(null, context, "SMS_INBOUND", "SMS_ROUTE_CREATED",
				"SMS_ROUTE", route.getId(), Map.of()));
		return RouteResponse.from(route);
	}

	@Transactional
	public RouteResponse deactivate(UUID routeId, AuditCallContext context) {
		SmsRoute route = routes.findByIdAndActiveTrue(routeId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SMS_ROUTE_NOT_FOUND", "SMS route was not found"));
		route.deactivate(clock.instant());
		audit.record(AuditCommand.success(null, context, "SMS_INBOUND", "SMS_ROUTE_DISABLED",
				"SMS_ROUTE", route.getId(), Map.of()));
		return RouteResponse.from(route);
	}

	@Transactional(readOnly = true)
	public SmsRoute resolve(String deviceId, String recipient, Integer simNumber) {
		SmsRoute byRecipient = recipient == null ? null : routes
				.findByDeviceIdAndRecipientAndActiveTrue(deviceId, recipient).orElse(null);
		SmsRoute bySim = simNumber == null ? null : routes
				.findByDeviceIdAndSimNumberAndActiveTrue(deviceId, simNumber).orElse(null);
		if (byRecipient != null && bySim != null && !byRecipient.getTenantId().equals(bySim.getTenantId())) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SMS_ROUTE_CONFLICT",
					"SMS routing signals disagree");
		}
		SmsRoute route = byRecipient == null ? bySim : byRecipient;
		if (route == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
				"SMS_ROUTE_UNKNOWN", "SMS route was not found");
		return route;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(UUID routeId) {
		routes.lockActive(routeId).orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
				"SMS_ROUTE_UNKNOWN", "SMS route was not found"));
	}

	public String normalized(String phone) {
		String compact = phone.replaceAll("[\\s()\\-]", "");
		if (compact.matches("[1-9][0-9]{8}")) compact = "+48" + compact;
		if (!compact.matches("\\+[1-9][0-9]{7,14}")) throw invalid();
		return compact;
	}

	private ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "SMS_ROUTE_INVALID", "SMS route is invalid");
	}

	private ApiException conflict() {
		return new ApiException(HttpStatus.CONFLICT, "SMS_ROUTE_CONFLICT", "SMS route already exists");
	}
}
