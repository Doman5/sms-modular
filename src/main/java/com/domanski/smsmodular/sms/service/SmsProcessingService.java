package com.domanski.smsmodular.sms.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.absence.service.AbsenceDayService;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.employee.dto.EmployeeDtos.SmsEmployeeMatch;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;
import com.domanski.smsmodular.employee.service.EmployeeService;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.integrationruntime.service.IntegrationRuntimeService;
import com.domanski.smsmodular.sms.dto.SmsDtos.ResolveRequest;
import com.domanski.smsmodular.sms.dto.SmsDtos.SmsResponse;
import com.domanski.smsmodular.sms.entity.SmsMessage;
import com.domanski.smsmodular.sms.repository.SmsMessageRepository;
import com.domanski.smsmodular.tenancy.service.TenantService;
import com.domanski.smsmodular.time.service.TimeTrackingService;

@Service
@RequiredArgsConstructor
public class SmsProcessingService {
	private final SmsMessageRepository messages;
	private final SmsCrypto crypto;
	private final SmsRuleParser parser;
	private final EmployeeService employees;
	private final EntitlementService entitlements;
	private final TenantService tenants;
	private final TimeTrackingService time;
	private final AbsenceDayService absences;
	private final IntegrationRuntimeService runtime;
	private final AuditService audit;
	private final Clock clock;

	@Transactional
	public void process(UUID tenantId, UUID messageId, UUID jobId, String owner) {
		entitlements.require(tenantId, "SMS_INBOUND");
		SmsMessage message = requireLocked(tenantId, messageId);
		if (!message.getStatus().equals("PENDING")) {
			runtime.complete(tenantId, jobId, owner);
			return;
		}
		String sender = crypto.decrypt(tenantId, message.getSenderCipher());
		String content = crypto.decrypt(tenantId, message.getContentCipher());
		if (sender == null || content == null) {
			message.expire(clock.instant());
			runtime.complete(tenantId, jobId, owner);
			return;
		}
		SmsEmployeeMatch employee = employees.findByPhone(tenantId, sender);
		if (employee == null || employee.status() != EmployeeStatus.ACTIVE) {
			message.review(employee == null ? null : employee.id(), employee == null
					? "EMPLOYEE_UNKNOWN" : "EMPLOYEE_INACTIVE", clock.instant());
			runtime.complete(tenantId, jobId, owner);
			return;
		}
		ZoneId zone = ZoneId.of(tenants.get(tenantId).timeZone());
		var result = parser.parse(content, message.getReceivedAt(), zone);
		if (result.reviewReason() != null) {
			message.review(employee.id(), result.reviewReason(), clock.instant());
			runtime.complete(tenantId, jobId, owner);
			return;
		}
		AuditCallContext context = AuditCallContext.system("sms-" + message.getId());
		if (result.category().equals("WORK_TIME")) {
			time.createFromSms(tenantId, employee.id(), messageId, result.date(),
					result.startTime(), result.endTime(), context);
		} else {
			absences.createFromSms(tenantId, employee.id(), messageId, result.date(), context);
		}
		message.complete(employee.id(), result.category(), clock.instant());
		audit.record(AuditCommand.success(tenantId, context, "SMS_INBOUND", "SMS_RESOLVED",
				"SMS_MESSAGE", messageId, Map.of()));
		runtime.complete(tenantId, jobId, owner);
	}

	@Transactional
	public void reviewAfterConflict(UUID tenantId, UUID messageId, UUID jobId, String owner) {
		SmsMessage message = requireLocked(tenantId, messageId);
		if (message.getStatus().equals("PENDING")) message.review(message.getEmployeeId(), "CONFLICT", clock.instant());
		runtime.complete(tenantId, jobId, owner);
	}

	@Transactional
	public void markDead(UUID tenantId, UUID messageId) {
		SmsMessage message = requireLocked(tenantId, messageId);
		if (message.getStatus().equals("PENDING")) message.error(clock.instant());
	}

	@Transactional
	public SmsResponse resolve(UUID tenantId, UUID messageId, ResolveRequest request,
			AuditCallContext context) {
		entitlements.require(tenantId, "SMS_INBOUND");
		SmsMessage message = requireLocked(tenantId, messageId);
		if (request == null || request.version() == null || request.version() != message.getVersion()) {
			throw new ApiException(HttpStatus.CONFLICT, "SMS_VERSION_CONFLICT", "SMS was changed");
		}
		if (!message.getStatus().equals("REVIEW_REQUIRED") && !message.getStatus().equals("ERROR")) {
			throw new ApiException(HttpStatus.CONFLICT, "SMS_ALREADY_RESOLVED", "SMS cannot be resolved");
		}
		String category = request.category();
		if (category == null) throw invalid();
		if (category.equals("DISMISS")) {
			message.dismiss(clock.instant());
		} else {
			if (request.employeeId() == null) throw invalid();
			if (employees.get(tenantId, request.employeeId()).status() != EmployeeStatus.ACTIVE) {
				throw new ApiException(HttpStatus.CONFLICT, "SMS_EMPLOYEE_INACTIVE", "Employee is not active");
			}
			if (category.equals("WORK_TIME")) {
				if (request.workDate() == null || request.startTime() == null || request.endTime() == null) throw invalid();
				time.createFromSms(tenantId, request.employeeId(), messageId, request.workDate(),
						request.startTime(), request.endTime(), context);
			} else if (category.equals("ABSENCE")) {
				if (request.absenceDate() == null) throw invalid();
				absences.createFromSms(tenantId, request.employeeId(), messageId, request.absenceDate(), context);
			} else throw invalid();
			message.complete(request.employeeId(), category, clock.instant());
		}
		audit.record(AuditCommand.success(tenantId, context, "SMS_INBOUND", "SMS_RESOLVED",
				"SMS_MESSAGE", messageId, Map.of()));
		messages.flush();
		return response(message, true);
	}

	@Transactional
	public SmsResponse reparse(UUID tenantId, UUID messageId, long version, AuditCallContext context) {
		entitlements.require(tenantId, "SMS_INBOUND");
		SmsMessage message = requireLocked(tenantId, messageId);
		if (message.getVersion() != version) throw new ApiException(HttpStatus.CONFLICT,
				"SMS_VERSION_CONFLICT", "SMS was changed");
		if (!message.getStatus().equals("REVIEW_REQUIRED") && !message.getStatus().equals("ERROR")) {
			throw new ApiException(HttpStatus.CONFLICT, "SMS_ALREADY_RESOLVED", "SMS cannot be reparsed");
		}
		message.pending(clock.instant());
		runtime.requeue(tenantId, messageId);
		audit.record(AuditCommand.success(tenantId, context, "SMS_INBOUND", "SMS_REPARSE_REQUESTED",
				"SMS_MESSAGE", messageId, Map.of()));
		messages.flush();
		return response(message, true);
	}

	public SmsResponse response(SmsMessage message, boolean full) {
		String sender = full ? crypto.decrypt(message.getTenantId(), message.getSenderCipher()) : mask(message);
		String content = full ? crypto.decrypt(message.getTenantId(), message.getContentCipher()) : null;
		return SmsResponse.from(message, sender, content);
	}

	private String mask(SmsMessage message) {
		String sender = crypto.decrypt(message.getTenantId(), message.getSenderCipher());
		if (sender == null) return null;
		return sender.length() <= 4 ? "****" : "****" + sender.substring(sender.length() - 4);
	}

	private SmsMessage requireLocked(UUID tenantId, UUID messageId) {
		return messages.lock(tenantId, messageId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
				"SMS_NOT_FOUND", "SMS was not found"));
	}

	private ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "SMS_RESOLUTION_INVALID", "SMS resolution is invalid");
	}
}
