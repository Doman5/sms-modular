package com.domanski.smsmodular.sms.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.common.api.PageResponse;
import com.domanski.smsmodular.entitlements.service.EntitlementService;
import com.domanski.smsmodular.sms.dto.SmsDtos.SmsResponse;
import com.domanski.smsmodular.sms.repository.SmsMessageRepository;

@Service
@RequiredArgsConstructor
public class SmsQueryService {
	private final SmsMessageRepository messages;
	private final SmsProcessingService processing;
	private final EntitlementService entitlements;

	@Transactional(readOnly = true)
	public PageResponse<SmsResponse> list(UUID tenantId, Instant from, Instant to,
			String status, UUID employeeId, boolean reviewOnly, Pageable pageable) {
		entitlements.require(tenantId, "SMS_INBOUND");
		if (from == null || to == null || !to.isAfter(from) || to.isAfter(from.plus(366, ChronoUnit.DAYS))
				|| status != null && !status.matches("PENDING|COMPLETED|REVIEW_REQUIRED|DISMISSED|ERROR|EXPIRED")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_FILTER_INVALID", "SMS filters are invalid");
		}
		Pageable fixed = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
				Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id")));
		return PageResponse.from(messages.search(tenantId, from, to, status, employeeId, reviewOnly, fixed)
				.map(message -> processing.response(message, false)));
	}

	@Transactional(readOnly = true)
	public SmsResponse get(UUID tenantId, UUID id) {
		entitlements.require(tenantId, "SMS_INBOUND");
		return processing.response(messages.findByTenantIdAndId(tenantId, id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SMS_NOT_FOUND", "SMS was not found")), true);
	}
}
