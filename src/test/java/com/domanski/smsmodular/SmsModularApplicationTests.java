package com.domanski.smsmodular;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.domanski.smsmodular.support.FoundationTestConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@Import(FoundationTestConfiguration.class)
class SmsModularApplicationTests {

	@Test
	void contextLoads() {
	}

}
