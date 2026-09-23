package com.domanski.smsmodular.sms;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import com.domanski.smsmodular.sms.service.SmsRuleParser;
import static org.assertj.core.api.Assertions.assertThat;

class SmsRuleParserTest {
	private final SmsRuleParser parser = new SmsRuleParser();
	private final ZoneId zone = ZoneId.of("Europe/Warsaw");
	private final Instant receivedAt = Instant.parse("2025-08-03T22:30:00Z");

	@Test
	void parsesExplicitWorkRangeAndTenantLocalDate() {
		var result = parser.parse("Praca 08:15-16:30", receivedAt, zone);
		assertThat(result.category()).isEqualTo("WORK_TIME");
		assertThat(result.date()).isEqualTo(LocalDate.of(2025, 8, 4));
		assertThat(result.startTime()).isEqualTo(LocalTime.of(8, 15));
		assertThat(result.endTime()).isEqualTo(LocalTime.of(16, 30));
	}

	@Test
	void typeAndHoursWithoutRangeNeedReview() {
		assertThat(parser.parse("urlop 10.08.2025", receivedAt, zone).reviewReason())
				.isEqualTo("CATEGORY_NOT_SUPPORTED");
		assertThat(parser.parse("8h", receivedAt, zone).reviewReason()).isEqualTo("AMBIGUOUS_CONTENT");
		assertThat(parser.parse("nie będzie mnie 2025-08-05", receivedAt, zone).category())
				.isEqualTo("ABSENCE");
	}
}
