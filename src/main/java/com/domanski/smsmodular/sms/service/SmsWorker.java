package com.domanski.smsmodular.sms.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.domanski.smsmodular.common.api.ApiException;
import com.domanski.smsmodular.integrationruntime.service.IntegrationRuntimeService;

@Component
@RequiredArgsConstructor
public class SmsWorker {
	private final SmsSettings settings;
	private final SmsCrypto crypto;
	private final IntegrationRuntimeService runtime;
	private final SmsProcessingService processing;

	@Scheduled(fixedDelayString = "${app.sms.worker-delay-ms:5000}")
	public void run() {
		if (!settings.enabled() || !settings.workerEnabled()) return;
		crypto.requireEnabled();
		String owner = UUID.randomUUID().toString();
		for (var job : runtime.claim(owner)) {
			try {
				processing.process(job.tenantId(), job.smsMessageId(), job.id(), owner);
			} catch (ApiException exception) {
				if (exception.getStatus().equals(HttpStatus.CONFLICT)) {
					processing.reviewAfterConflict(job.tenantId(), job.smsMessageId(), job.id(), owner);
				} else fail(job.tenantId(), job.smsMessageId(), job.id(), owner);
			} catch (RuntimeException exception) {
				fail(job.tenantId(), job.smsMessageId(), job.id(), owner);
			}
		}
	}

	private void fail(UUID tenantId, UUID messageId, UUID jobId, String owner) {
		if (runtime.fail(tenantId, jobId, owner, "SMS_PROCESSING_FAILED")) {
			processing.markDead(tenantId, messageId);
		}
	}
}
