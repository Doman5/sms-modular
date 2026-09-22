package com.domanski.smsmodular.tenancy.application;

@FunctionalInterface
public interface TenantLifecycleEventPublisher {

	void publish(TenantLifecycleEvent event);
}
