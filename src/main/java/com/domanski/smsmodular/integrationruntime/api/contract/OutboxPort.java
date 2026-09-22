package com.domanski.smsmodular.integrationruntime.api.contract;


@FunctionalInterface
public interface OutboxPort {

	void publish(OutboxEvent event);
}
