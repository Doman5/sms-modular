package com.domanski.smsmodular.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.domanski.smsmodular.support.FoundationTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FoundationTestConfiguration.class)
class SystemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsSystemInformation() throws Exception {
		mockMvc.perform(get("/api/system/info"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applicationName").value("SMS Modular"))
			.andExpect(jsonPath("$.environment").value("local"))
			.andExpect(jsonPath("$.version").value("dev"));
	}

}
