package com.domanski.smsmodular.sms.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.domanski.smsmodular.common.api.ApiException;

@Component
@RequiredArgsConstructor
public class SmsCrypto {
	private final SmsSettings settings;
	private final SecureRandom random = new SecureRandom();

	public String encrypt(UUID tenantId, String value) {
		try {
			byte[] iv = new byte[12];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
			cipher.updateAAD(tenantId.toString().getBytes(StandardCharsets.UTF_8));
			byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
					.put(iv).put(encrypted).array());
		} catch (Exception exception) {
			throw new IllegalStateException("SMS encryption failed", exception);
		}
	}

	public String decrypt(UUID tenantId, String value) {
		if (value == null) return null;
		try {
			byte[] packed = Base64.getDecoder().decode(value);
			byte[] iv = new byte[12];
			ByteBuffer buffer = ByteBuffer.wrap(packed);
			buffer.get(iv);
			byte[] encrypted = new byte[buffer.remaining()];
			buffer.get(encrypted);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
			cipher.updateAAD(tenantId.toString().getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			throw new IllegalStateException("SMS decryption failed", exception);
		}
	}

	public void requireEnabled() {
		if (!settings.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
				"SMS_INTEGRATION_DISABLED", "SMS integration is disabled");
		if (settings.deviceId() == null || settings.deviceId().isBlank()
				|| settings.signingKey() == null || settings.signingKey().isBlank()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_INTEGRATION_NOT_CONFIGURED",
					"SMS integration is not configured");
		}
		key();
	}

	private SecretKeySpec key() {
		try {
			byte[] bytes = Base64.getDecoder().decode(settings.dataKeyBase64());
			if (bytes.length != 32) throw new IllegalArgumentException();
			return new SecretKeySpec(bytes, "AES");
		} catch (Exception exception) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DATA_KEY_INVALID",
					"SMS data key is not configured");
		}
	}
}
