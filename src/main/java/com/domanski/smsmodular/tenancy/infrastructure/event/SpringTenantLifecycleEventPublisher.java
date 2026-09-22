package com.domanski.smsmodular.tenancy.infrastructure.event;

import com.domanski.smsmodular.tenancy.application.TenantLifecycleEvent;
import com.domanski.smsmodular.tenancy.application.TenantLifecycleEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;


@Component
public final class SpringTenantLifecycleEventPublisher implements TenantLifecycleEventPublisher {

	private final ApplicationEventPublisher publisher;

	public SpringTenantLifecycleEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public void publish(TenantLifecycleEvent event) {
		publisher.publishEvent(event);
	}
}
