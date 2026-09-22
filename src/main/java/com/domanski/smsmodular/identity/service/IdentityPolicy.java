package com.domanski.smsmodular.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.domanski.smsmodular.common.api.ApiException;

@Component
public class IdentityPolicy {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private final SecureRandom random = new SecureRandom();

	public String email(String value) {
		String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_INVALID", "Email is invalid");
		}
		return email;
	}

	public String name(String value) {
		String name = value == null ? "" : value.trim();
		if (name.isBlank() || name.length() > 160) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_INVALID", "Name is invalid");
		}
		return name;
	}

	public String roleCode(String value) {
		String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_CODE_INVALID", "Role code is invalid");
		}
		return code;
	}

	public String roleName(String value) {
		String name = value == null ? "" : value.trim();
		if (name.isBlank() || name.length() > 120) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NAME_INVALID", "Role name is invalid");
		}
		return name;
	}

	public void password(String value) {
		if (value == null || value.length() < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_INVALID", "Password must contain at least 12 characters and at most 72 bytes");
		}
	}

	public String temporaryPassword() {
		byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
