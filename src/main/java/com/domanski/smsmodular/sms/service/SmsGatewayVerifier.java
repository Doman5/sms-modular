package com.domanski.smsmodular.sms.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.domanski.smsmodular.common.api.ApiException;

@Component
@RequiredArgsConstructor
public class SmsGatewayVerifier {
	private final SmsSettings settings;
	private final SmsCrypto crypto;
	private final Clock clock;

	public void verify(byte[] body, String signature, String timestamp) {
		crypto.requireEnabled();
		if (signature == null || timestamp == null || body == null || body.length > 16384) throw invalid();
		try {
			long seconds = Long.parseLong(timestamp);
			if (Math.abs(clock.instant().getEpochSecond() - seconds) > 300) throw invalid();
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(settings.signingKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			mac.update(body);
			byte[] expected = mac.doFinal(timestamp.getBytes(StandardCharsets.UTF_8));
			String hex = signature.startsWith("sha256=") ? signature.substring(7) : signature;
			if (hex.length() != 64) throw invalid();
			byte[] provided = new byte[32];
			for (int index = 0; index < 32; index++) {
				int high = Character.digit(hex.charAt(index * 2), 16);
				int low = Character.digit(hex.charAt(index * 2 + 1), 16);
				if (high < 0 || low < 0) throw invalid();
				provided[index] = (byte) ((high << 4) | low);
			}
			if (!MessageDigest.isEqual(expected, provided)) throw invalid();
		} catch (ApiException exception) {
			throw exception;
		} catch (Exception exception) {
			throw invalid();
		}
	}

	private ApiException invalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "SMS_SIGNATURE_INVALID", "SMS webhook signature is invalid");
	}
}
