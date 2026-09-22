package com.domanski.smsmodular;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmsModularApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmsModularApplication.class, args);
	}

}
