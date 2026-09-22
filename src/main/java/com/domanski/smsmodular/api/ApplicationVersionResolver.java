package com.domanski.smsmodular.api;

final class ApplicationVersionResolver {

	private ApplicationVersionResolver() {
	}

	static String resolve() {
		String implementationVersion = ApplicationVersionResolver.class
			.getPackage()
			.getImplementationVersion();
		return implementationVersion == null ? "dev" : implementationVersion;
	}

}
