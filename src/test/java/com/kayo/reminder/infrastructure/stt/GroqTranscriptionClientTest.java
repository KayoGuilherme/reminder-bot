package com.kayo.reminder.infrastructure.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.config.AppProperties.Stt;
import com.kayo.reminder.infrastructure.http.RestClients;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import com.kayo.reminder.testsupport.FakeHttpServer;
import com.kayo.reminder.testsupport.LogCapture;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroqTranscriptionClientTest {

	private static final String KEY = "groq-secret-key";
	private static final byte[] AUDIO = "OggS-fake-audio-bytes".getBytes();

	private FakeHttpServer server;

	@BeforeEach
	void start() throws IOException {
		server = new FakeHttpServer();
	}

	@AfterEach
	void stop() {
		server.close();
	}

	private GroqTranscriptionClient client(String language, Duration readTimeout) {
		Stt settings = new Stt(language, "whisper-large-v3-turbo", server.baseUrl(), Duration.ofSeconds(2), readTimeout);
		return new GroqTranscriptionClient(RestClients.create(server.baseUrl(), settings.connectTimeout(), readTimeout),
				KEY, settings, new SingleRetry("Transcription", Duration.ZERO, d -> { }));
	}

	private GroqTranscriptionClient client() {
		return client("pt", Duration.ofSeconds(5));
	}

	@Test
	void sendsModelLanguageKeyAndAudioFileAndReturnsTrimmedText() {
		server.reply(200, "{\"text\":\"  preciso enviar o relatório até as 18h \",\"x_groq\":{\"id\":\"req_1\"}}");

		assertThat(client().transcribe(AUDIO)).isEqualTo("preciso enviar o relatório até as 18h");

		FakeHttpServer.Request request = server.requests().get(0);
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.path()).isEqualTo("/audio/transcriptions");
		assertThat(request.header("authorization")).isEqualTo("Bearer " + KEY);
		assertThat(request.header("content-type")).startsWith("multipart/form-data");
		String body = request.bodyText();
		assertThat(body).contains("name=\"model\"").contains("whisper-large-v3-turbo");
		assertThat(body).contains("name=\"language\"").contains("\r\n\r\npt\r\n");
		assertThat(body).contains("name=\"file\"").contains("filename=\"voice.ogg\"").contains("audio/ogg");
		assertThat(body).contains("OggS-fake-audio-bytes");
	}

	@Test
	void languageCanBeOverridden() {
		server.reply(200, "{\"text\":\"hello\"}");
		client("en", Duration.ofSeconds(5)).transcribe(AUDIO);
		assertThat(server.requests().get(0).bodyText()).contains("\r\n\r\nen\r\n");
	}

	@Test
	void blankTextIsAnInvalidResponse() {
		server.reply(200, "{\"text\":\"   \"}");
		assertThatThrownBy(() -> client().transcribe(AUDIO))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.INVALID_RESPONSE));
	}

	@Test
	void nonJsonAnswerIsAnInvalidResponse() {
		server.reply(200, "<html>oops</html>");
		assertThatThrownBy(() -> client().transcribe(AUDIO))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.INVALID_RESPONSE));
	}

	@Test
	void serviceUnavailableThenSuccessMakesTwoRequests() {
		server.reply(503, "{\"error\":\"busy\"}").reply(200, "{\"text\":\"ok\"}");
		assertThat(client().transcribe(AUDIO)).isEqualTo("ok");
		assertThat(server.requests()).hasSize(2);
	}

	@Test
	void twoTimeoutsFailAfterExactlyTwoRequests() {
		server.replySlowly(200, "{\"text\":\"late\"}", 1500).replySlowly(200, "{\"text\":\"late\"}", 1500);
		assertThatThrownBy(() -> client("pt", Duration.ofMillis(300)).transcribe(AUDIO))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.UNAVAILABLE));
		assertThat(server.requests()).hasSize(2);
	}

	@Test
	void badKeyIsRejectedWithoutRetry() {
		server.reply(401, "{\"error\":{\"message\":\"Invalid API Key\"}}");
		assertThatThrownBy(() -> client().transcribe(AUDIO))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.REJECTED));
		assertThat(server.requests()).hasSize(1);
	}

	@Test
	void logsContainNeitherTheKeyNorTheTranscript() {
		server.reply(500, "{\"text\":\"transcript-text-leak\"}").reply(500, "{\"text\":\"transcript-text-leak\"}");
		try (LogCapture logs = new LogCapture(SingleRetry.class)) {
			assertThatThrownBy(() -> client().transcribe(AUDIO)).isInstanceOf(ExternalServiceException.class);
			assertThat(logs.allText()).contains("HTTP 500").doesNotContain(KEY).doesNotContain("transcript-text-leak");
		}
	}
}
