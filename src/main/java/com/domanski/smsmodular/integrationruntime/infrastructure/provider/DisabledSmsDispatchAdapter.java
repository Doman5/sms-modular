package com.domanski.smsmodular.integrationruntime.infrastructure.provider;

import com.domanski.smsmodular.integrationruntime.api.contract.SmsDispatchCommand;
import com.domanski.smsmodular.integrationruntime.api.contract.SmsDispatchPort;
import com.domanski.smsmodular.integrationruntime.api.contract.SmsDispatchResult;
import com.domanski.smsmodular.integrationruntime.application.IntegrationRuntimeFailure;
import com.domanski.smsmodular.integrationruntime.infrastructure.metrics.IntegrationRuntimeMetrics;
import org.springframework.stereotype.Component;


@Component
public class DisabledSmsDispatchAdapter implements SmsDispatchPort {

	private final IntegrationRuntimeMetrics metrics;

	public DisabledSmsDispatchAdapter(IntegrationRuntimeMetrics metrics) {
		this.metrics = metrics;
	}

	@Override
	public SmsDispatchResult dispatch(SmsDispatchCommand command) {
		var sample = metrics.startProviderTimer();
		metrics.providerStatus("DISABLED");
		try {
			throw new IntegrationRuntimeFailure(
				"SMS_PROVIDER_NOT_CONFIGURED",
				"Outbound SMS transport is not configured for this deployment."
			);
		} finally {
			metrics.providerFailure();
			metrics.stopProviderTimer(sample);
		}
	}
}
