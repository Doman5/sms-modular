package com.domanski.smsmodular.usage.api;

public record UsageSnapshot(String metric, long used, String mode, Long limit, Long remaining) {
}
