package com.domanski.smsmodular.integrationruntime.domain;


public enum OutboxStatus {
	PENDING,
	PROCESSING,
	RETRY_WAIT,
	COMPLETED,
	DEAD_LETTER
}
