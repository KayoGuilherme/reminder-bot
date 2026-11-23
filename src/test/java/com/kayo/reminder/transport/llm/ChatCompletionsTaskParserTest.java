package com.kayo.reminder.transport.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.config.LlmProperties;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.FakeHttpServer;
import com.kayo.reminder.testsupport.FakeRequest;
import com.kayo.reminder.testsupport.LogCapture;
import com.kayo.reminder.transport.http.RestClients;
import com.kayo.reminder.transport.http.SingleRetry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatCompletionsTaskParserTest {

	private static final String KEY = "llm-secret-key";
	private static final String TEXT = "preciso enviar o relatório até as 18h, é urgente";
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T13:00:00Z"), ZoneOffset.UTC);
	private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");

	private FakeHttpServer server;

	@BeforeEach
	void start() throws IOException {
		server = new FakeHttpServer();
	}

	@AfterEach
	void stop() {
		server.close();
	}

	private ChatCompletionsTaskParser parser(String baseUrl) {
		LlmProperties settings = new LlmProperties(baseUrl, "test-model", KEY, Duration.ofSeconds(2), Duration.ofSeconds(5));
		return new ChatCompletionsTaskParser(RestClients.create(baseUrl, settings.connectTimeout(), settings.readTimeout()),
				settings, CLOCK, ZONE, new SingleRetry("Task parser", Duration.ZERO, d -> { }));
	}

	private ChatCompletionsTaskParser parser() {
		return parser(server.baseUrl());
	}

	private static String completion(String content) {
		String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
		return "{\"id\":\"c1\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped
				+ "\",\"reasoning\":\"hidden\"},\"finish_reason\":\"stop\"}],\"usage\":{}}";
	}

	@Test
	void parsesTheAcceptanceExample() {
		server.reply(200, completion("{\"title\":\"Enviar o relatório\",\"urgency\":\"HIGH\",\"startAt\":null,"
				+ "\"deadlineAt\":\"2026-11-22T18:00:00-03:00\"}"));

		ParsedTaskResponse task = parser().parse(TEXT);

		assertThat(task).isEqualTo(new ParsedTaskResponse("Enviar o relatório", Urgency.HIGH, null,
				Instant.parse("2026-11-22T21:00:00Z")));
	}

	@Test
	void requestCarriesModelKeyCurrentTimeAndKeepsTheUserTextApart() {
		server.reply(200, completion("{\"title\":\"x\"}"));
		parser().parse(TEXT);

		FakeRequest request = server.requests().get(0);
		assertThat(request.path()).isEqualTo("/chat/completions");
		assertThat(request.header("authorization")).isEqualTo("Bearer " + KEY);
		String body = new String(request.body(), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(body).contains("\"model\":\"test-model\"").contains("\"temperature\":0")
				.contains("json_object")
				.contains("2026-11-22 10:00 -03:00");
		assertThat(body).contains("\"role\":\"system\"").contains("\"role\":\"user\"");
		assertThat(body.split(java.util.regex.Pattern.quote(TEXT), -1)).hasSize(2);
	}

	@Test
	void honorsAnotherBaseUrl() throws IOException {
		try (FakeHttpServer other = new FakeHttpServer()) {
			other.reply(200, completion("{\"title\":\"x\"}"));
			parser(other.baseUrl() + "/v1beta/openai/").parse(TEXT);
			assertThat(other.requests().get(0).path()).isEqualTo("/v1beta/openai/chat/completions");
			assertThat(server.requests()).isEmpty();
		}
	}

	@Test
	void badGatewayThenSuccessMakesTwoRequests() {
		server.reply(502, "bad gateway").reply(200, completion("{\"title\":\"x\"}"));
		assertThat(parser().parse(TEXT).title()).isEqualTo("x");
		assertThat(server.requests()).hasSize(2);
	}

	@Test
	void rejectedKeyMakesOneRequest() {
		server.reply(401, "{\"error\":{\"message\":\"bad key\"}}");
		assertThatThrownBy(() -> parser().parse(TEXT))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(ExternalFailureKind.REJECTED));
		assertThat(server.requests()).hasSize(1);
	}

	@Test
	void nonJsonContentIsAnInvalidResponse() {
		server.reply(200, completion("Sure, here is your task"));
		assertThatThrownBy(() -> parser().parse(TEXT))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(ExternalFailureKind.INVALID_RESPONSE));
	}

	@Test
	void emptyChoicesAreAnInvalidResponse() {
		server.reply(200, "{\"choices\":[]}");
		assertThatThrownBy(() -> parser().parse(TEXT))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(ExternalFailureKind.INVALID_RESPONSE));
	}

	@Test
	void logsContainNeitherUserTextNorModelTextNorKey() {
		server.reply(200, completion("model-says-secret-words"));
		try (LogCapture parserLogs = new LogCapture(ChatCompletionsTaskParser.class);
				LogCapture validatorLogs = new LogCapture(ParsedTaskValidator.class);
				LogCapture retryLogs = new LogCapture(SingleRetry.class)) {
			assertThatThrownBy(() -> parser().parse(TEXT)).isInstanceOf(ExternalServiceException.class);
			String all = parserLogs.allText() + validatorLogs.allText() + retryLogs.allText();
			assertThat(all).doesNotContain(TEXT).doesNotContain("model-says-secret-words").doesNotContain(KEY);
		}
	}
}
