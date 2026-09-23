package com.domanski.smsmodular.sms.service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SmsRuleParser {
	private static final Pattern DATE = Pattern.compile("(?:^|\\s)(\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})(?:$|\\s)");
	private static final Pattern WORK = Pattern.compile("^(?:praca\\s+)?(\\d{1,2}):(\\d{2})\\s*[-–]\\s*(\\d{1,2}):(\\d{2})$");
	private static final Pattern ABSENCE = Pattern.compile("^(?:nie bedzie mnie|mnie nie bedzie|nie przyjde|nie bede w pracy)$");
	private static final Pattern CATEGORY = Pattern.compile(".*\\b(?:urlop|l4|chorob\\w*|zwolnien\\w*|na zadanie|n/z)\\b.*");

	public ParseResult parse(String content, Instant receivedAt, ZoneId zone) {
		String text = Normalizer.normalize(content.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ");
		Matcher dateMatcher = DATE.matcher(text);
		LocalDate date = receivedAt.atZone(zone).toLocalDate();
		if (dateMatcher.find()) {
			String raw = dateMatcher.group(1);
			try {
				date = raw.contains(".") ? LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd.MM.uuuu"))
						: LocalDate.parse(raw);
			} catch (DateTimeParseException exception) {
				return ParseResult.review("AMBIGUOUS_CONTENT");
			}
			text = (text.substring(0, dateMatcher.start()) + " " + text.substring(dateMatcher.end())).trim();
			if (DATE.matcher(text).find()) return ParseResult.review("AMBIGUOUS_CONTENT");
		}
		if (CATEGORY.matcher(text).matches()) return ParseResult.review("CATEGORY_NOT_SUPPORTED");
		Matcher work = WORK.matcher(text);
		if (work.matches()) {
			try {
				LocalTime start = LocalTime.of(Integer.parseInt(work.group(1)), Integer.parseInt(work.group(2)));
				LocalTime end = LocalTime.of(Integer.parseInt(work.group(3)), Integer.parseInt(work.group(4)));
				if (!start.equals(end)) return new ParseResult("WORK_TIME", date, start, end, null);
			} catch (RuntimeException ignored) {
			}
		}
		if (ABSENCE.matcher(text).matches()) return new ParseResult("ABSENCE", date, null, null, null);
		return ParseResult.review("AMBIGUOUS_CONTENT");
	}

	public record ParseResult(String category, LocalDate date, LocalTime startTime, LocalTime endTime,
			String reviewReason) {
		public static ParseResult review(String reason) {
			return new ParseResult(null, null, null, null, reason);
		}
	}
}
