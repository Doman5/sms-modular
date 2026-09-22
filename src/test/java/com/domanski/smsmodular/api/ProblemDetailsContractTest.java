package com.domanski.smsmodular.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import com.domanski.smsmodular.common.domain.ConflictException;
import com.domanski.smsmodular.common.domain.DomainException;
import com.domanski.smsmodular.support.FoundationTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ProblemDetailsContractTest.FixtureController.class, FoundationTestConfiguration.class})
@ActiveProfiles("test")
class ProblemDetailsContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void mapsValidationToRfc9457WithFieldErrorsAndCorrelationId() throws Exception {
		mockMvc.perform(post("/api/test/foundation/validation")
				.with(user("tester"))
				.header("X-Correlation-Id", "contract-validation-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"value\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string("X-Correlation-Id", "contract-validation-1"))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.message").value("The request contains invalid values."))
			.andExpect(jsonPath("$.correlationId").value("contract-validation-1"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
	}

	@Test
	void protectsTenantAndPlatformApisButLeavesSystemAndOpenApiPublic() throws Exception {
		mockMvc.perform(get("/api/v1/protected"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.correlationId").isNotEmpty());

		mockMvc.perform(get("/api/platform/v1/protected").with(user("tenant-user")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(get("/api/system/info"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk());
	}

	@Test
	void mapsConflictAndDomainFailuresWithoutChangingTheirContract() throws Exception {
		mockMvc.perform(get("/api/test/foundation/conflict").with(user("tester")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.correlationId").isNotEmpty());

		mockMvc.perform(get("/api/test/foundation/domain").with(user("tester")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("DOMAIN_ERROR"));
	}

	@Test
	void hidesUnexpectedExceptionDetails() throws Exception {
		mockMvc.perform(get("/api/test/foundation/failure").with(user("tester")))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("An unexpected error occurred."))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("table"))));
	}

	@RestController
	@RequestMapping("/api/test/foundation")
	static class FixtureController {

		@PostMapping("/validation")
		void validation(@Valid @RequestBody ValidationRequest request) {
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new ConflictException("A record with this key already exists.");
		}

		@GetMapping("/domain")
		void domain() {
			throw new DomainException("The requested state transition is not allowed.");
		}

		@GetMapping("/failure")
		void failure() {
			throw new IllegalStateException("database table secret_details must not leak");
		}
	}

	record ValidationRequest(@NotBlank String value) {
	}
}
