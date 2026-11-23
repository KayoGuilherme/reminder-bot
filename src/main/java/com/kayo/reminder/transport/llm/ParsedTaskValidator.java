package com.kayo.reminder.transport.llm;

import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.domain.models.enums.Urgency;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class ParsedTaskValidator {

	private static final String SERVICE = "Task parser";

	private static final Map<String, String> LANGUAGE_BY_PREFIX = new LinkedHashMap<>(Map.of("pt", "pt-BR", "en", "en"));

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
	private final ZoneId zone;

	public ParsedTaskValidator(ZoneId zone) {
		this.zone = zone;
	}

	public ParsedTaskResponse validate(String content) {
		JsonNode root = parseObject(content);
		String title = Optional.ofNullable(text(root, "title")).map(String::strip).orElse("");
		Instant start = instant(text(root, "startAt"));
		Instant deadline = instant(text(root, "deadlineAt"));
		Instant usableStart = Optional.ofNullable(start)
				.filter(candidate -> deadline == null || !candidate.isAfter(deadline)).orElse(null);
		ParsedTaskResponse parsed = new ParsedTaskResponse(title, urgency(text(root, "urgency")), usableStart, deadline,
				language(text(root, "language")));
		Optional.of(validator.validate(parsed)).filter(violations -> !violations.isEmpty()).ifPresent(violations -> {
			throw invalid();
		});
		return parsed;
	}

	private JsonNode parseObject(String content) {
		String json = Optional.ofNullable(content).filter(text -> !text.isBlank())
				.orElseThrow(ParsedTaskValidator::invalid);
		try {
			return Optional.ofNullable(mapper.readTree(unfence(json))).filter(JsonNode::isObject)
					.orElseThrow(ParsedTaskValidator::invalid);
		}
		catch (JacksonException unreadable) {
			throw invalid();
		}
	}

	private static String unfence(String content) {
		String text = content.strip();
		return Optional.of(text).filter(fenced -> fenced.startsWith("```")).map(ParsedTaskValidator::withoutFence)
				.orElse(text).strip();
	}

	private static String withoutFence(String fenced) {
		int firstLineEnd = fenced.indexOf('\n');
		String body = firstLineEnd < 0 ? "" : fenced.substring(firstLineEnd + 1);
		String trimmed = body.stripTrailing();
		return trimmed.endsWith("```") ? trimmed.substring(0, trimmed.length() - 3) : body;
	}

	private static String text(JsonNode root, String field) {
		return Optional.ofNullable(root.get(field)).filter(JsonNode::isString).map(JsonNode::asString).orElse(null);
	}

	private static String language(String value) {
		return Optional.ofNullable(value).map(text -> text.strip().toLowerCase(Locale.ROOT))
				.flatMap(tag -> LANGUAGE_BY_PREFIX.entrySet().stream().filter(entry -> tag.startsWith(entry.getKey()))
						.map(Map.Entry::getValue).findFirst())
				.orElse(null);
	}

	private static Urgency urgency(String value) {
		return Optional.ofNullable(value).flatMap(ParsedTaskValidator::parseUrgency).orElse(null);
	}

	private static Optional<Urgency> parseUrgency(String value) {
		try {
			return Optional.of(Urgency.valueOf(value.strip().toUpperCase(Locale.ROOT)));
		}
		catch (IllegalArgumentException unknown) {
			return Optional.empty();
		}
	}

	private Instant instant(String value) {
		return Optional.ofNullable(value).map(String::strip).filter(text -> !text.isEmpty())
				.flatMap(text -> offsetInstant(text).or(() -> localInstant(text))).orElse(null);
	}

	private static Optional<Instant> offsetInstant(String text) {
		try {
			return Optional.of(OffsetDateTime.parse(text).toInstant());
		}
		catch (DateTimeParseException notOffset) {
			return Optional.empty();
		}
	}

	private Optional<Instant> localInstant(String text) {
		try {
			return Optional.of(LocalDateTime.parse(text).atZone(zone).toInstant());
		}
		catch (DateTimeParseException notLocal) {
			return Optional.empty();
		}
	}

	private static ExternalServiceException invalid() {
		return new ExternalServiceException(ExternalFailureKind.INVALID_RESPONSE, SERVICE);
	}
}
