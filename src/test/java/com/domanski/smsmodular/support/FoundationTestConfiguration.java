package com.domanski.smsmodular.support;

import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.InboxReceiptRepository;
import com.domanski.smsmodular.integrationruntime.infrastructure.persistence.OutboxRepository;
import com.domanski.smsmodular.audit.infrastructure.persistence.AuditRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.domanski.smsmodular.tenancy.infrastructure.persistence.TenantRepository;


@TestConfiguration(proxyBeanMethods = false)
public class FoundationTestConfiguration {

	@Bean
	TenantRepository tenantRepository() {
		return new TenantRepository();
	}

	@Bean
	OutboxRepository outboxRepository() {
		return new OutboxRepository();
	}

	@Bean
	InboxReceiptRepository inboxReceiptRepository() {
		return new InboxReceiptRepository();
	}

	@Bean
	AuditRepository auditRepository() {
		return new AuditRepository();
	}
}
