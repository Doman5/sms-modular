package com.domanski.smsmodular.integrationruntime.api.contract;





@FunctionalInterface
public interface SmsDispatchPort {

	SmsDispatchResult dispatch(SmsDispatchCommand command);
}
