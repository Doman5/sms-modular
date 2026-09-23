package com.domanski.smsmodular.sms.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.domanski.smsmodular.audit.api.AuditCallContext;
import com.domanski.smsmodular.sms.dto.SmsDtos.InboundResponse;
import com.domanski.smsmodular.sms.service.SmsInboundService;

@RestController
@RequestMapping("/api/integrations/v1/sms/sms-gate")
@RequiredArgsConstructor
public class SmsInboundController {
	private final SmsInboundService inbound;

	@PostMapping("/inbound")
	public ResponseEntity<InboundResponse> receive(@RequestBody byte[] body,
			@RequestHeader(value = "X-Signature", required = false) String signature,
			@RequestHeader(value = "X-Timestamp", required = false) String timestamp,
			HttpServletRequest request) {
		InboundResponse result = inbound.receive(body, signature, timestamp,
				AuditCallContext.correlationId(request));
		return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result);
	}
}
