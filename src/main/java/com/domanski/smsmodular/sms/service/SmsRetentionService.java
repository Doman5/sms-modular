package com.domanski.smsmodular.sms.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.integrationruntime.service.IntegrationRuntimeService;
import com.domanski.smsmodular.sms.repository.SmsMessageRepository;

@Service
@RequiredArgsConstructor
public class SmsRetentionService {
	private final SmsMessageRepository messages;
	private final IntegrationRuntimeService runtime;
	private final Clock clock;

	@Scheduled(cron = "0 15 2 * * *", zone = "UTC")
	@Transactional
	public void expire() {
		var cutoff = clock.instant().minus(90, ChronoUnit.DAYS);
		while (true) {
			var selected = messages.findTop100ByReceivedAtBeforeAndSenderCipherIsNotNullOrderByReceivedAtAsc(cutoff);
			if (selected.isEmpty()) return;
			for (var message : selected) {
				message.expire(clock.instant());
				runtime.expire(message.getTenantId(), message.getId());
			}
			messages.flush();
		}
	}
}
