package com.domanski.smsmodular.integrationruntime.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.domanski.smsmodular.integrationruntime.entity.InboxReceipt;
import com.domanski.smsmodular.integrationruntime.entity.OutboxMessage;
import com.domanski.smsmodular.integrationruntime.repository.InboxReceiptRepository;
import com.domanski.smsmodular.integrationruntime.repository.OutboxMessageRepository;

@Service
@RequiredArgsConstructor
public class IntegrationRuntimeService {
	private final InboxReceiptRepository inbox;
	private final OutboxMessageRepository outbox;
	private final Clock clock;

	@Transactional(readOnly = true)
	public UUID existingMessageId(String provider, String deviceId, String eventId) {
		return inbox.findByProviderAndDeviceIdAndEventId(provider, deviceId, eventId)
				.map(InboxReceipt::getSmsMessageId).orElse(null);
	}

	@Transactional
	public void enqueue(UUID tenantId, UUID smsMessageId, String provider, String deviceId, String eventId) {
		inbox.saveAndFlush(new InboxReceipt(tenantId, smsMessageId, provider, deviceId, eventId, clock.instant()));
		outbox.save(new OutboxMessage(tenantId, smsMessageId, clock.instant()));
	}

	@Transactional
	public List<ClaimedJob> claim(String owner) {
		var now = clock.instant();
		var selected = outbox.claimable(now);
		selected.forEach(job -> job.lease(owner, now));
		outbox.flush();
		return selected.stream().map(job -> new ClaimedJob(job.getId(), job.getTenantId(), job.getSmsMessageId())).toList();
	}

	@Transactional
	public void complete(UUID tenantId, UUID jobId, String owner) {
		var job = outbox.findByTenantIdAndId(tenantId, jobId)
				.filter(value -> value.ownedBy(owner) && value.getLeaseUntil().isAfter(clock.instant()))
				.orElseThrow(() -> new IllegalStateException("SMS job lease was lost"));
		job.complete(clock.instant());
	}

	@Transactional
	public boolean fail(UUID tenantId, UUID jobId, String owner, String code) {
		var job = outbox.findByTenantIdAndId(tenantId, jobId).filter(value -> value.ownedBy(owner)).orElse(null);
		if (job == null) return false;
		job.fail(code, clock.instant());
		return job.getStatus().equals("DEAD_LETTER");
	}

	@Transactional
	public void requeue(UUID tenantId, UUID smsMessageId) {
		outbox.save(new OutboxMessage(tenantId, smsMessageId, clock.instant()));
	}

	@Transactional
	public void expire(UUID tenantId, UUID smsMessageId) {
		outbox.findByTenantIdAndSmsMessageId(tenantId, smsMessageId).stream()
				.filter(job -> !job.getStatus().equals("DONE") && !job.getStatus().equals("DEAD_LETTER"))
				.forEach(job -> job.complete(clock.instant()));
	}

	public record ClaimedJob(UUID id, UUID tenantId, UUID smsMessageId) {
	}
}
