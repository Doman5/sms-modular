package com.domanski.smsmodular.sms.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.domanski.smsmodular.sms.entity.SmsMessage;
import com.domanski.smsmodular.sms.entity.SmsRoute;

public final class SmsDtos {
	private SmsDtos() {
	}

	public record GateEvent(String deviceId, String event, String id, GatePayload payload, String webhookId) {
	}

	public record GatePayload(String messageId, String message, String sender, String phoneNumber,
			String recipient, Integer simNumber, String receivedAt) {
	}

	public record InboundResponse(UUID id, String status, boolean created) {
	}

	public record SmsResponse(UUID id, UUID employeeId, String sender, String content,
			String recipient, Instant receivedAt, String status, String reviewReason,
			String resolution, long version) {
		public static SmsResponse from(SmsMessage message, String sender, String content) {
			return new SmsResponse(message.getId(), message.getEmployeeId(), sender, content,
					message.getRecipient(), message.getReceivedAt(), message.getStatus(),
					message.getReviewReason(), message.getResolution(), message.getVersion());
		}
	}

	public record ResolveRequest(@NotNull @PositiveOrZero Long version, @NotNull String category,
			UUID employeeId, LocalDate workDate, LocalTime startTime, LocalTime endTime,
			LocalDate absenceDate) {
	}

	public record CreateRouteRequest(@NotNull UUID tenantId, String recipient, Integer simNumber) {
	}

	public record RouteResponse(UUID id, UUID tenantId, String deviceId, String recipient,
			Integer simNumber, boolean active) {
		public static RouteResponse from(SmsRoute route) {
			return new RouteResponse(route.getId(), route.getTenantId(), route.getDeviceId(),
					route.getRecipient(), route.getSimNumber(), route.isActive());
		}
	}
}
