package com.domanski.smsmodular.identity.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformBootstrap {

	@Bean
	ApplicationRunner bootstrapPlatformAccount(PlatformBootstrapAccountService service) {
		return arguments -> service.bootstrap();
	}
}
