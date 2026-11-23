package com.kayo.reminder.transport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ParsedTaskValidatorTest {

	private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
	private static final Instant TOMORROW_9 = Instant.parse("2026-11-23T12:00:00Z");
	private static final String DEADLINE = "\"deadlineAt\":\"2026-11-23T09:00:00-03:00\"";

	private final ParsedTaskValidator validator = new ParsedTaskValidator(ZONE);

	static Stream<Arguments> valid() {
		return Stream.of(
				Arguments.of("complete, lower-case urgency",
						"{\"title\":\"Pay rent\",\"urgency\":\"high\",\"startAt\":null," + DEADLINE + "}",
						new ParsedTaskResponse("Pay rent", Urgency.HIGH, null, TOMORROW_9)),
				Arguments.of("wrapped in a json fence",
						"```json\n{\"title\":\"Pay rent\",\"urgency\":\"high\"," + DEADLINE + "}\n```",
						new ParsedTaskResponse("Pay rent", Urgency.HIGH, null, TOMORROW_9)),
				Arguments.of("wrapped in a bare fence",
						"```\n{\"title\":\"Pay rent\"," + DEADLINE + "}\n```",
						new ParsedTaskResponse("Pay rent", null, null, TOMORROW_9)),
				Arguments.of("unknown urgency becomes absent",
						"{\"title\":\"Pay rent\",\"urgency\":\"URGENT\"," + DEADLINE + "}",
						new ParsedTaskResponse("Pay rent", null, null, TOMORROW_9)),
				Arguments.of("null deadline",
						"{\"title\":\"Pay rent\",\"urgency\":\"LOW\",\"deadlineAt\":null}",
						new ParsedTaskResponse("Pay rent", Urgency.LOW, null, null)),
				Arguments.of("unparseable deadline",
						"{\"title\":\"Pay rent\",\"urgency\":\"LOW\",\"deadlineAt\":\"tomorrow morning\"}",
						new ParsedTaskResponse("Pay rent", Urgency.LOW, null, null)),
				Arguments.of("date without time is not a deadline",
						"{\"title\":\"Pay rent\",\"deadlineAt\":\"2026-11-23\"}",
						new ParsedTaskResponse("Pay rent", null, null, null)),
				Arguments.of("deadline without offset is read in the display zone",
						"{\"title\":\"Pay rent\",\"deadlineAt\":\"2026-11-23T09:00:00\"}",
						new ParsedTaskResponse("Pay rent", null, null, TOMORROW_9)),
				Arguments.of("deadline in UTC",
						"{\"title\":\"Pay rent\",\"deadlineAt\":\"2026-11-23T12:00:00Z\"}",
						new ParsedTaskResponse("Pay rent", null, null, TOMORROW_9)),
				Arguments.of("start after deadline is dropped",
						"{\"title\":\"Pay rent\",\"startAt\":\"2026-11-24T09:00:00-03:00\"," + DEADLINE + "}",
						new ParsedTaskResponse("Pay rent", null, null, TOMORROW_9)),
				Arguments.of("start before deadline is kept",
						"{\"title\":\"Pay rent\",\"startAt\":\"2026-11-22T09:00:00-03:00\"," + DEADLINE + "}",
						new ParsedTaskResponse("Pay rent", null, Instant.parse("2026-11-22T12:00:00Z"), TOMORROW_9)),
				Arguments.of("past deadline passes through unchanged",
						"{\"title\":\"Pay rent\",\"deadlineAt\":\"2020-01-01T00:00:00-03:00\"}",
						new ParsedTaskResponse("Pay rent", null, null, Instant.parse("2020-01-01T03:00:00Z"))),
				Arguments.of("title is trimmed and extra fields ignored",
						"{\"title\":\"  Pay rent \",\"mood\":\"tired\"," + DEADLINE + "}",
						new ParsedTaskResponse("Pay rent", null, null, TOMORROW_9)),
				Arguments.of("non-string urgency is absent",
						"{\"title\":\"Pay rent\",\"urgency\":3}",
						new ParsedTaskResponse("Pay rent", null, null, null)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("valid")
	void validAnswers(String name, String answer, ParsedTaskResponse expected) {
		assertThat(validator.validate(answer)).isEqualTo(expected);
	}

	static Stream<Arguments> invalid() {
		return Stream.of(
				Arguments.of("blank title", "{\"title\":\"   \"," + DEADLINE + "}"),
				Arguments.of("missing title", "{\"urgency\":\"LOW\"}"),
				Arguments.of("non-string title", "{\"title\":42}"),
				Arguments.of("501-char title", "{\"title\":\"" + "x".repeat(501) + "\"}"),
				Arguments.of("prose", "Sure! Here is your task: Pay rent"),
				Arguments.of("array", "[]"),
				Arguments.of("scalar", "\"Pay rent\""),
				Arguments.of("empty", ""),
				Arguments.of("unclosed fence with prose", "```json\nnot json\n```"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalid")
	void invalidAnswers(String name, String answer) {
		assertThatThrownBy(() -> validator.validate(answer))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(ExternalFailureKind.INVALID_RESPONSE))
				.hasMessageNotContaining("Pay rent");
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.CsvSource(value = { "pt,pt-BR", "pt-BR,pt-BR", "PT,pt-BR", "en,en", "en-US,en", "es,", "xx," })
	void theLanguageIsNormalizedToOneOfTheSupportedOnesOrAbsent(String answered, String expected) {
		assertThat(validator.validate("{\"title\":\"x\",\"language\":\"" + answered + "\"}").language())
				.isEqualTo(expected);
	}

	@Test
	void aMissingOrNonTextLanguageIsAbsent() {
		assertThat(validator.validate("{\"title\":\"x\"}").language()).isNull();
		assertThat(validator.validate("{\"title\":\"x\",\"language\":null}").language()).isNull();
		assertThat(validator.validate("{\"title\":\"x\",\"language\":5}").language()).isNull();
	}

	@Test
	void titleOfExactlyMaxLengthIsAccepted() {
		assertThat(validator.validate("{\"title\":\"" + "x".repeat(500) + "\"}").title()).hasSize(500);
	}

	@Test
	void nullContentIsInvalid() {
		assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(ExternalServiceException.class);
	}
}
