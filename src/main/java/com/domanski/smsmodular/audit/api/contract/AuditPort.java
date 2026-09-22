package com.domanski.smsmodular.audit.api.contract;


@FunctionalInterface
public interface AuditPort {

	void record(AuditCommand command);
}
