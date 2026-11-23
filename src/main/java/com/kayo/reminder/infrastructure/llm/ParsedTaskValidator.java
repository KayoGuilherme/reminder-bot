package com.kayo.reminder.infrastructure.llm;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.application.port.out.ParsedTask;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.Urgency;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks what the model answered; nothing in it is trusted. A usable title is the only hard requirement. Any other
 * value that is wrong is treated as absent, because the flow already knows what to do when urgency or a time is
 * missing. Messages carry no model output.
 */
class ParsedTaskValidator {

	private static final String SERVICE = "Task parser";

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final ZoneId zone;

	ParsedTaskValidator(ZoneId zone) {
		this.zone = zone;
	}

	ParsedTask validate(String content) {
		if (content == null || content.isBlank()) {
			throw invalid();
		}
		JsonNode root;
		try {
			root = mapper.readTree(unfence(content));
		}
		catch (JacksonException ex) {
			throw invalid();
		}
		if (root == null || !root.isObject()) {
			throw invalid();
		}
		String title = text(root, "title");
		title = title == null ? "" : title.strip();
		if (title.isEmpty() || title.length() > Task.MAX_TITLE_LENGTH) {
			throw invalid();
		}
		Instant start = instant(text(root, "startAt"));
		Instant deadline = instant(text(root, "deadlineAt"));
		if (start != null && deadline != null && start.isAfter(deadline)) {
			start = null;
		}
		return new ParsedTask(title, urgency(text(root, "urgency")), start, deadline, language(text(root, "language")));
	}

	/** Removes a markdown code fence (with or without a language tag) around the JSON. */
	private static String unfence(String content) {
		String text = content.strip();
		if (text.startsWith("```")) {
			int firstLineEnd = text.indexOf('\n');
			text = firstLineEnd < 0 ? "" : text.substring(firstLineEnd + 1);
			if (text.stripTrailing().endsWith("```")) {
				text = text.stripTrailing();
				text = text.substring(0, text.length() - 3);
			}
		}
		return text.strip();
	}

	private static String text(JsonNode root, String field) {
		JsonNode node = root.get(field);
		return node != null && node.isString() ? node.asString() : null;
	}

	/** Only the two supported languages are kept; anything else is absent and the configured language is used. */
	private static String language(String value) {
		if (value == null) {
			return null;
		}
		String tag = value.strip().toLowerCase(Locale.ROOT);
		if (tag.startsWith("pt")) {
			return "pt-BR";
		}
		return tag.startsWith("en") ? "en" : null;
	}

	private static Urgency urgency(String value) {
		if (value == null) {
			return null;
		}
		try {
			return Urgency.valueOf(value.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** ISO-8601 with an offset, or without one (then read in the display timezone); anything else is absent. */
	private Instant instant(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.strip();
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException ignored) {
			// fall through to the local form
		}
		try {
			return LocalDateTime.parse(text).atZone(zone).toInstant();
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static ExternalServiceException invalid() {
		return new ExternalServiceException(Kind.INVALID_RESPONSE, SERVICE);
	}
}
