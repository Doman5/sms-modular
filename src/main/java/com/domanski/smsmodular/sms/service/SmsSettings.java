package com.domanski.smsmodular.sms.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sms")
public record SmsSettings(boolean enabled, boolean workerEnabled, String deviceId,
		String signingKey, String dataKeyBase64) {
}
