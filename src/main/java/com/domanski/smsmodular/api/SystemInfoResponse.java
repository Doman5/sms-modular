package com.domanski.smsmodular.api;

public record SystemInfoResponse(
	String applicationName,
	String environment,
	String version
) {
}
