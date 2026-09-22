package com.domanski.smsmodular.api;

import com.domanski.smsmodular.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

	private final AppProperties appProperties;

	public SystemController(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@GetMapping("/info")
	public SystemInfoResponse getSystemInfo() {
		return new SystemInfoResponse(
			"SMS Modular",
			appProperties.environment(),
			ApplicationVersionResolver.resolve()
		);
	}

}
