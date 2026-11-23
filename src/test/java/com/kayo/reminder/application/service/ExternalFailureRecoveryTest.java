package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.RestClients;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import com.kayo.reminder.infrastructure.llm.ChatCompletionsTaskParser;
import com.kayo.reminder.infrastructure.stt.GroqTranscriptionClient;
import com.kayo.reminder.testsupport.FakeHttpServer;
import com.kayo.reminder.testsupport.InMemoryTaskRepository;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The real speech-to-text and LLM clients against fake servers: a failed call is answered with a friendly message
 * and nothing is saved, and the very next message is handled normally.
 */
class ExternalFailureRecoveryTest {

	private static final long CHAT = 100L;
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final String PARSED = "{\"title\":\"Enviar o relatório\",\"urgency\":\"HIGH\",\"startAt\":null,"
			+ "\"deadlineAt\":\"2026-11-22T18:00:00-03:00\"}";

	private FakeHttpServer stt;
	private FakeHttpServer llm;
	private final RecordingSender sender = new RecordingSender();
	private final InMemoryTaskRepository repository = new InMemoryTaskRepository();
	private CreateTaskFromVoiceService service;

	@BeforeEach
	void setUp() throws Exception {
		stt = new FakeHttpServer();
		llm = new FakeHttpServer();
		MutableClock clock = new MutableClock(NOW);
		ZoneId zone = ZoneId.of("America/Fortaleza");
		AppProperties properties = TestProperties.of(zone, "en");
		AppProperties.Stt sttSettings = new AppProperties.Stt("pt", "m", stt.baseUrl(), Duration.ofSeconds(2),
				Duration.ofSeconds(5));
		AppProperties.Llm llmSettings = new AppProperties.Llm(llm.baseUrl(), "m", "k", Duration.ofSeconds(2),
				Duration.ofSeconds(5));
		service = new CreateTaskFromVoiceService(sender, fileId -> new byte[] { 1, 2, 3 },
				new GroqTranscriptionClient(RestClients.create(stt.baseUrl(), sttSettings.connectTimeout(),
						sttSettings.readTimeout()), "key", sttSettings, new SingleRetry("Transcription", Duration.ZERO, d -> { })),
				new ChatCompletionsTaskParser(RestClients.create(llm.baseUrl(), llmSettings.connectTimeout(),
						llmSettings.readTimeout()), llmSettings, clock, zone, new SingleRetry("Task parser", Duration.ZERO, d -> { })),
				repository, new DraftStore(clock, Duration.ofMinutes(10)), TestMessages.of("en"), properties, clock);
	}

	@AfterEach
	void tearDown() {
		stt.close();
		llm.close();
	}

	private static String completion(String content) {
		String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
		return "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped
				+ "\"},\"finish_reason\":\"stop\"}]}";
	}

	private static String text(String key) {
		return TestMessages.text("en", key);
	}

	@Test
	void aSpeechToTextOutageGetsATryAgainReplyAndTheNextVoiceNoteWorks() {
		stt.reply(503, "{}").reply(503, "{}").reply(200, "{\"text\":\"enviar o relatório até as 18h, é urgente\"}");
		llm.reply(200, completion(PARSED));

		service.submit(IncomingCommand.voice(CHAT, 42, 1, "file-1", 5));

		assertThat(stt.requests()).hasSize(2);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("task.audio-failed"));
		assertThat(sender.withButtons).isEmpty();
		assertThat(repository.tasks).isEmpty();
		assertThat(llm.requests()).isEmpty();

		service.submit(IncomingCommand.voice(CHAT, 42, 2, "file-2", 5));

		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("Enviar o relatório").contains("2026-11-22 18:00");
		assertThat(repository.tasks).isEmpty(); // saved only after Confirm
	}

	@Test
	void aSingleTransientSpeechToTextFailureIsRetriedWithoutTheOwnerNoticing() {
		stt.reply(503, "{}").reply(200, "{\"text\":\"enviar o relatório até as 18h\"}");
		llm.reply(200, completion(PARSED));

		service.submit(IncomingCommand.voice(CHAT, 42, 1, "file-1", 5));

		assertThat(stt.requests()).hasSize(2);
		assertThat(sender.texts).isEmpty();
		assertThat(sender.withButtons).hasSize(1);
	}

	@Test
	void anLlmOutageGetsATryAgainReplyAndTheNextTextWorks() {
		llm.reply(503, "{}").reply(503, "{}").reply(200, completion(PARSED));

		service.submit(IncomingCommand.text(CHAT, 42, 1, "enviar o relatório até as 18h, é urgente"));

		assertThat(llm.requests()).hasSize(2);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("task.parse-failed"));
		assertThat(sender.withButtons).isEmpty();
		assertThat(repository.tasks).isEmpty();

		service.submit(IncomingCommand.text(CHAT, 42, 2, "enviar o relatório até as 18h, é urgente"));

		assertThat(llm.requests()).hasSize(3);
		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("Enviar o relatório");
	}

	@Test
	void anAuthenticationFailureIsNotRetriedAndAlsoDoesNotBlockTheNextMessage() {
		llm.reply(401, "{\"error\":\"bad key\"}").reply(200, completion(PARSED));

		service.submit(IncomingCommand.text(CHAT, 42, 1, "enviar o relatório até as 18h"));
		assertThat(llm.requests()).hasSize(1);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("task.parse-failed"));

		service.submit(IncomingCommand.text(CHAT, 42, 2, "enviar o relatório até as 18h"));
		assertThat(sender.withButtons).hasSize(1);
	}
}
