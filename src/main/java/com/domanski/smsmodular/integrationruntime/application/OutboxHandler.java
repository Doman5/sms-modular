package com.domanski.smsmodular.integrationruntime.application;

import com.domanski.smsmodular.integrationruntime.domain.OutboxMessage;


public interface OutboxHandler {

	String topic();

	int payloadVersion();

	
	default String consumer() {
		return topic() + ":v" + payloadVersion();
	}

	void handle(OutboxMessage message, JobContext context) throws Exception;
}
