package com.domanski.smsmodular.entitlements.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.entitlements.api.EntitlementSnapshot;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.AddonView;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.LimitOverrideView;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.SubscriptionView;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateAddonRequest;
import com.domanski.smsmodular.entitlements.dto.SubscriptionDtos.UpdateLimitRequest;
import com.domanski.smsmodular.entitlements.entity.ModuleCatalog;
import com.domanski.smsmodular.entitlements.entity.PlanVersion;
import com.domanski.smsmodular.entitlements.entity.TenantAddon;
import com.domanski.smsmodular.entitlements.entity.TenantLimitOverride;
import com.domanski.smsmodular.entitlements.entity.TenantSubscription;
import com.domanski.smsmodular.entitlements.repository.ModuleCatalogRepository;
import com.domanski.smsmodular.entitlements.repository.PlanModuleRepository;
import com.domanski.smsmodular.entitlements.repository.PlanVersionRepository;
import com.domanski.smsmodular.entitlements.repository.TenantAddonRepository;
import com.domanski.smsmodular.entitlements.repository.TenantLimitOverrideRepository;
import com.domanski.smsmodular.entitlements.repository.TenantSubscriptionRepository;
import com.domanski.smsmodular.tenancy.api.TenantStatus;
import com.domanski.smsmodular.tenancy.service.TenantService;
import com.domanski.smsmodular.usage.api.UsageSnapshot;

@Service
@RequiredArgsConstructor
public class EntitlementService {

	public static final UUID BASE_VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
	public static final String ACTIVE_USERS = "ACTIVE_USERS";
	public static final String ACTIVE_EMPLOYEES = "ACTIVE_EMPLOYEES";

	private final ModuleCatalogRepository catalog;
	private final PlanVersionRepository versions;
	private final PlanModuleRepository planModules;
	private final TenantSubscriptionRepository subscriptions;
	private final TenantAddonRepository addons;
	private final TenantLimitOverrideRepository overrides;
	private final TenantService tenants;
	private final AuditService audit;
	private final Clock clock;

	@Transactional(propagation = Propagation.MANDATORY)
	public void assignBasePlan(UUID tenantId) {
		if (subscriptions.findByTenantId(tenantId).isPresent()) return;
		subscriptions.save(new TenantSubscription(UUID.randomUUID(), tenantId, BASE_VERSION_ID, clock.instant()));
	}

	@Transactional(readOnly = true)
	public EntitlementSnapshot snapshot(UUID tenantId) {
		Instant now = clock.instant();
		TenantSubscription subscription = requireSubscription(tenantId);
		PlanVersion version = versions.findById(subscription.getPlanVersionId()).orElseThrow(this::missingPlan);
		Set<String> active = new HashSet<>();
		boolean tenantActive = tenants.get(tenantId).status() == TenantStatus.ACTIVE;
		boolean subscriptionActive = subscription.getStatus().equals("ACTIVE")
				&& !now.isBefore(subscription.getStartsAt())
				&& (subscription.getEndsAt() == null || now.isBefore(subscription.getEndsAt()));
		Instant validUntil = futureBoundary(now, subscription.getEndsAt(), subscription.getStartsAt());
		if (tenantActive && subscriptionActive) {
			planModules.findByPlanVersionId(version.getId()).forEach(module -> catalog.findById(module.getModuleKey())
					.filter(entry -> entry.getStatus().equals("AVAILABLE")).ifPresent(entry -> active.add(entry.getKey())));
		}
		for (TenantAddon addon : addons.findByTenantId(tenantId)) {
			if (!addon.getStatus().equals("ENABLED")) continue;
			validUntil = futureBoundary(now, validUntil, addon.getStartsAt(), addon.getEndsAt());
			if (tenantActive && subscriptionActive && !now.isBefore(addon.getStartsAt())
					&& (addon.getEndsAt() == null || now.isBefore(addon.getEndsAt()))) {
				catalog.findById(addon.getModuleKey())
						.filter(entry -> entry.getStatus().equals("AVAILABLE"))
						.ifPresent(entry -> active.add(entry.getKey()));
			}
		}
		String mode = version.getActiveUserLimitMode();
		Long limit = version.getActiveUserLimit();
		String employeeMode = version.getActiveEmployeeLimitMode();
		Long employeeLimit = version.getActiveEmployeeLimit();
		var override = overrides.findByTenantIdAndMetricCode(tenantId, ACTIVE_USERS);
		if (override.isPresent()) {
			TenantLimitOverride value = override.get();
			validUntil = futureBoundary(now, validUntil, value.getExpiresAt());
			if (!value.getMode().equals("INHERIT")
					&& (value.getExpiresAt() == null || now.isBefore(value.getExpiresAt()))) {
				mode = value.getMode();
				limit = value.getLimitValue();
			}
		}
		var employeeOverride = overrides.findByTenantIdAndMetricCode(tenantId, ACTIVE_EMPLOYEES);
		if (employeeOverride.isPresent()) {
			TenantLimitOverride value = employeeOverride.get();
			validUntil = futureBoundary(now, validUntil, value.getExpiresAt());
			if (!value.getMode().equals("INHERIT")
					&& (value.getExpiresAt() == null || now.isBefore(value.getExpiresAt()))) {
				employeeMode = value.getMode();
				employeeLimit = value.getLimitValue();
			}
		}
		return new EntitlementSnapshot(Set.copyOf(active), mode, limit, employeeMode, employeeLimit, validUntil);
	}

	@Transactional(readOnly = true)
	public SubscriptionView view(UUID tenantId, UsageSnapshot usage, UsageSnapshot employeeUsage) {
		TenantSubscription subscription = requireSubscription(tenantId);
		PlanVersion version = versions.findById(subscription.getPlanVersionId()).orElseThrow(this::missingPlan);
		EntitlementSnapshot effective = snapshot(tenantId);
		Map<String, TenantAddon> assignments = new HashMap<>();
		addons.findByTenantId(tenantId).forEach(addon -> assignments.put(addon.getModuleKey(), addon));
		Set<String> baseKeys = new HashSet<>();
		planModules.findByPlanVersionId(version.getId()).forEach(module -> baseKeys.add(module.getModuleKey()));
		List<AddonView> modules = catalog.findAllByOrderByTypeAscKeyAsc().stream().map(entry -> {
			TenantAddon assigned = assignments.get(entry.getKey());
			String status = baseKeys.contains(entry.getKey()) ? "INCLUDED" : assigned == null
					? "DISABLED" : assigned.getStatus();
			return new AddonView(entry.getKey(), entry.getType(), entry.getStatus(), status,
					assigned == null ? null : assigned.getStartsAt(),
					assigned == null ? null : assigned.getEndsAt(), entry.getDependsOn());
		}).toList();
		LimitOverrideView limitOverride = overrides.findByTenantIdAndMetricCode(tenantId, ACTIVE_USERS)
				.map(value -> new LimitOverrideView(value.getMode(), value.getLimitValue(), value.getExpiresAt()))
				.orElse(new LimitOverrideView("INHERIT", null, null));
		LimitOverrideView employeeLimitOverride = overrides
				.findByTenantIdAndMetricCode(tenantId, ACTIVE_EMPLOYEES)
				.map(value -> new LimitOverrideView(value.getMode(), value.getLimitValue(), value.getExpiresAt()))
				.orElse(new LimitOverrideView("INHERIT", null, null));
		return new SubscriptionView(tenantId, "BASE", version.getVersion(), effective.capabilities(),
				modules, usage, limitOverride, employeeUsage, employeeLimitOverride, effective.validUntil());
	}

	@Transactional(readOnly = true)
	public void require(UUID tenantId, String capability) {
		if (!snapshot(tenantId).capabilities().contains(capability)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "CAPABILITY_NOT_ENABLED", "Capability is not enabled");
		}
	}

	@Transactional
	public void updateAddon(UUID tenantId, String key, UpdateAddonRequest request, AuditCallContext context) {
		tenants.lockForAccessChange(tenantId);
		ModuleCatalog module = catalog.findById(key).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
				"MODULE_NOT_FOUND", "Module was not found"));
		if (!module.getType().equals("ADD_ON")) throw new ApiException(HttpStatus.BAD_REQUEST,
				"MODULE_NOT_ADDON", "Only add-on modules can be configured");
		Instant now = clock.instant();
		TenantAddon addon = addons.findByTenantIdAndModuleKey(tenantId, key).orElse(null);
		if (request == null || request.enabled() == null) throw new ApiException(HttpStatus.BAD_REQUEST,
				"ADDON_REQUEST_INVALID", "Add-on configuration is invalid");
		if (request.enabled()) {
			if (!module.getStatus().equals("AVAILABLE")) throw new ApiException(HttpStatus.CONFLICT,
					"MODULE_NOT_AVAILABLE", "Module is not available yet");
			Instant start = request.startsAt() == null ? now : request.startsAt();
			Instant end = request.endsAt();
			if ((end != null && (!end.isAfter(start) || !end.isAfter(now))))
				throw new ApiException(HttpStatus.BAD_REQUEST, "ADDON_DATES_INVALID", "Add-on dates are invalid");
			if (module.getDependsOn() != null) requireCoveringDependency(tenantId, module.getDependsOn(), start, end);
			if (key.equals("PROJECTS")) ensureProjectsCoversPlanning(tenantId, start, end);
			if (addon != null && addon.getStatus().equals("ENABLED") && addon.getStartsAt().equals(start)
					&& Objects.equals(addon.getEndsAt(), end)) return;
			if (addon == null) addon = new TenantAddon(UUID.randomUUID(), tenantId, key, start, end, now);
			else addon.configure(start, end, now);
			addons.save(addon);
		} else {
			if (request.startsAt() != null || request.endsAt() != null) throw new ApiException(HttpStatus.BAD_REQUEST,
					"ADDON_DATES_INVALID", "Disabled add-on cannot have dates");
			if (addon == null || addon.getStatus().equals("DISABLED")) return;
			if (key.equals("PROJECTS")) ensureProjectsCoversPlanning(tenantId, null, null);
			addon.disable(now);
		}
		audit.record(AuditCommand.success(tenantId, context, "ENTITLEMENTS",
				request.enabled() ? "ADDON_ENABLED" : "ADDON_DISABLED", "TENANT_ADDON", addon.getId(),
				Map.of("capability", key)));
	}

	@Transactional
	public void updateActiveUserLimit(UUID tenantId, UpdateLimitRequest request, AuditCallContext context) {
		updateLimit(tenantId, ACTIVE_USERS, request, context);
	}

	@Transactional
	public void updateLimit(UUID tenantId, String metric, UpdateLimitRequest request, AuditCallContext context) {
		if (!Set.of(ACTIVE_USERS, ACTIVE_EMPLOYEES).contains(metric)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "METRIC_NOT_FOUND", "Metric was not found");
		}
		tenants.lockForAccessChange(tenantId);
		Instant now = clock.instant();
		if (request == null || request.mode() == null
				|| !Set.of("FINITE", "UNLIMITED", "INHERIT").contains(request.mode())
				|| (request.mode().equals("FINITE") != (request.value() != null))
				|| (request.value() != null && request.value() < 1)
				|| (request.expiresAt() != null && (!request.expiresAt().isAfter(now)
						|| request.mode().equals("INHERIT")))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_INVALID", "Limit configuration is invalid");
		}
		TenantLimitOverride override = overrides.findByTenantIdAndMetricCode(tenantId, metric).orElse(null);
		if (override == null && request.mode().equals("INHERIT")) return;
		if (override != null && override.getMode().equals(request.mode())
				&& Objects.equals(override.getLimitValue(), request.value())
				&& Objects.equals(override.getExpiresAt(), request.expiresAt())) return;
		String previous = override == null ? "INHERIT" : override.getMode();
		Long previousLimit = override == null ? null : override.getLimitValue();
		if (override == null) override = new TenantLimitOverride(UUID.randomUUID(), tenantId, metric,
				request.mode(), request.value(), request.expiresAt(), now);
		else override.configure(request.mode(), request.value(), request.expiresAt(), now);
		overrides.save(override);
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("metric", metric);
		metadata.put("fromMode", previous);
		metadata.put("toMode", request.mode());
		if (previousLimit != null) metadata.put("fromLimit", previousLimit);
		if (request.value() != null) metadata.put("toLimit", request.value());
		audit.record(AuditCommand.success(tenantId, context, "ENTITLEMENTS", "LIMIT_UPDATED",
				"TENANT_LIMIT", override.getId(), metadata));
	}

	private void requireCoveringDependency(UUID tenantId, String key, Instant start, Instant end) {
		TenantAddon dependency = addons.findByTenantIdAndModuleKey(tenantId, key).orElse(null);
		if (dependency == null || !dependency.getStatus().equals("ENABLED")
				|| dependency.getStartsAt().isAfter(start)
				|| (dependency.getEndsAt() != null && (end == null || dependency.getEndsAt().isBefore(end)))) {
			throw new ApiException(HttpStatus.CONFLICT, "ADDON_DEPENDENCY_REQUIRED", "Required add-on is not active for the whole period");
		}
	}

	private void ensureProjectsCoversPlanning(UUID tenantId, Instant start, Instant end) {
		TenantAddon planning = addons.findByTenantIdAndModuleKey(tenantId, "PLANNING").orElse(null);
		if (planning == null || !planning.getStatus().equals("ENABLED")
				|| (planning.getEndsAt() != null && !planning.getEndsAt().isAfter(clock.instant()))) return;
		if (start == null || start.isAfter(planning.getStartsAt())
				|| (end != null && (planning.getEndsAt() == null || end.isBefore(planning.getEndsAt())))) {
			throw new ApiException(HttpStatus.CONFLICT, "ADDON_DEPENDENCY_IN_USE", "Planning requires Projects");
		}
	}

	private TenantSubscription requireSubscription(UUID tenantId) {
		return subscriptions.findByTenantId(tenantId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
				"SUBSCRIPTION_NOT_FOUND", "Subscription was not found"));
	}

	private ApiException missingPlan() {
		return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PLAN_MISSING", "Assigned plan is missing");
	}

	private Instant futureBoundary(Instant now, Instant... values) {
		Instant nearest = null;
		for (Instant value : values) if (value != null && value.isAfter(now)
				&& (nearest == null || value.isBefore(nearest))) nearest = value;
		return nearest;
	}
}
