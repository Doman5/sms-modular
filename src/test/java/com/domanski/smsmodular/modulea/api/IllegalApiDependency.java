package com.domanski.smsmodular.modulea.api;

import com.domanski.smsmodular.moduleb.domain.ForeignDomainType;
import com.domanski.smsmodular.moduleb.infrastructure.ForeignInfrastructureType;


public class IllegalApiDependency {

	public ForeignDomainType domain(ForeignInfrastructureType infrastructure) {
		return new ForeignDomainType(infrastructure.value());
	}
}
