package com.domanski.smsmodular.sms.service;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.audit.api.AuditCommand;
import com.domanski.smsmodular.audit.service.AuditService;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.integrationruntime.service.IntegrationRuntimeService;
import com.domanski.smsmodular.sms.dto.SmsDtos.GateEvent;
import com.domanski.smsmodular.sms.dto.SmsDtos.GatePayload;
import com.domanski.smsmodular.sms.dto.SmsDtos.InboundResponse;
import com.domanski.smsmodular.sms.entity.SmsMessage;
import com.domanski.smsmodular.sms.entity.SmsRoute;
import com.domanski.smsmodular.sms.repository.SmsMessageRepository;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class SmsInboundService {
	private final SmsGatewayVerifier verifier;
	private final SmsSettings settings;
	private final SmsCrypto crypto;
	private final SmsRouteService routes;
	private final SmsMessageRepository messages;
	private final IntegrationRuntimeService runtime;
	private final EntitlementService entitlements;
	private final AuditService audit;
	private final JsonMapper json;
	private final Clock clock;

	@Transactional
	public InboundResponse receive(byte[] body, String signature, String timestamp, String correlationId) {
		verifier.verify(body, signature, timestamp);
		GateEvent event;
		try {
			event = json.readValue(body, GateEvent.class);
		} catch (Exception exception) {
			throw invalid();
		}
		if (event == null || !"sms:received".equals(event.event()) || event.id() == null
				|| event.id().isBlank() || event.id().length() > 120 || event.deviceId() == null
				|| !event.deviceId().equals(settings.deviceId()) || event.payload() == null) throw invalid();
		GatePayload payload = event.payload();
		if (payload.messageId() == null || payload.messageId().isBlank() || payload.messageId().length() > 120
				|| payload.message() == null || payload.message().isBlank() || payload.message().length() > 500
				|| payload.receivedAt() == null || payload.simNumber() != null && payload.simNumber() < 0) throw invalid();
		String sender = payload.sender() == null ? payload.phoneNumber() : payload.sender();
		if (sender == null) throw invalid();
		try {
			sender = routes.normalized(sender);
		} catch (ApiException exception) {
			throw invalid();
		}
		String recipient = payload.recipient() == null || payload.recipient().isBlank()
				? null : routes.normalized(payload.recipient());
		Instant receivedAt;
		try {
			receivedAt = OffsetDateTime.parse(payload.receivedAt()).toInstant();
		} catch (Exception exception) {
			throw invalid();
		}
		String hash = hash(body);
		InboundResponse duplicate = duplicate(event, hash);
		if (duplicate != null) return duplicate;
		SmsRoute route = routes.resolve(event.deviceId(), recipient, payload.simNumber());
		routes.lock(route.getId());
		duplicate = duplicate(event, hash);
		if (duplicate != null) return duplicate;
		entitlements.require(route.getTenantId(), "SMS_INBOUND");
		SmsMessage message = new SmsMessage(route.getTenantId(), event.deviceId(), event.id(),
				payload.messageId(), recipient, payload.simNumber(), crypto.encrypt(route.getTenantId(), sender),
				crypto.encrypt(route.getTenantId(), payload.message()), hash, receivedAt, clock.instant());
		messages.saveAndFlush(message);
		runtime.enqueue(route.getTenantId(), message.getId(), "SMS_GATE", event.deviceId(), event.id());
		audit.record(AuditCommand.success(route.getTenantId(), AuditCallContext.system(correlationId),
				"SMS_INBOUND", "SMS_RECEIVED", "SMS_MESSAGE", message.getId(), Map.of()));
		return new InboundResponse(message.getId(), message.getStatus(), true);
	}

	private InboundResponse duplicate(GateEvent event, String hash) {
		SmsMessage existing = messages.findByProviderAndDeviceIdAndEventId("SMS_GATE", event.deviceId(), event.id())
				.orElse(null);
		if (existing == null) return null;
		if (!existing.getPayloadHash().equals(hash)) throw new ApiException(HttpStatus.CONFLICT,
				"SMS_EVENT_CONFLICT", "SMS event ID was reused with different payload");
		return new InboundResponse(existing.getId(), existing.getStatus(), false);
	}

	private String hash(byte[] body) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
		} catch (Exception exception) {
			throw new IllegalStateException("SMS payload hashing failed", exception);
		}
	}

	private ApiException invalid() {
		return new ApiException(HttpStatus.BAD_REQUEST, "SMS_PAYLOAD_INVALID", "SMS webhook payload is invalid");
	}
}
