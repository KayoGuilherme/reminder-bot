package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.component.DraftStore;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.config.LlmProperties;
import com.kayo.reminder.config.SttProperties;
import com.kayo.reminder.testsupport.FakeHttpServer;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.LogCapture;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import com.kayo.reminder.transport.http.RestClients;
import com.kayo.reminder.transport.http.SingleRetry;
import com.kayo.reminder.transport.llm.ChatCompletionsTaskParser;
import com.kayo.reminder.transport.stt.GroqTranscriptionClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogHygieneTest {

	private static final long CHAT = 100L;
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final String TRANSCRIPT = "remember to buy zebra-milk tomorrow at nine";
	private static final String TITLE = "Buy zebra-milk";
	private static final String GROQ_KEY = "groq-key-SECRET-123";
	private static final String LLM_KEY = "llm-key-SECRET-456";
	private static final String PARSED = "{\"title\":\"" + TITLE + "\",\"urgency\":\"HIGH\",\"startAt\":null,"
			+ "\"deadlineAt\":\"2026-11-22T18:00:00-03:00\"}";

	private FakeHttpServer stt;
	private FakeHttpServer llm;
	private final RecordingSender sender = new RecordingSender();
	private final InMemoryTaskService repository = new InMemoryTaskService();
	private final MutableClock clock = new MutableClock(NOW);
	private IncomingCommandService commands;

	@BeforeEach
	void setUp() throws Exception {
		stt = new FakeHttpServer();
		llm = new FakeHttpServer();
		ZoneId zone = ZoneId.of("America/Fortaleza");
		AppProperties properties = TestProperties.of(zone, "en");
		SttProperties sttSettings = new SttProperties("pt", "m", stt.baseUrl(), Duration.ofSeconds(2),
				Duration.ofSeconds(5));
		LlmProperties llmSettings = new LlmProperties(llm.baseUrl(), "m", LLM_KEY, Duration.ofSeconds(2),
				Duration.ofSeconds(5));
		var messages = TestMessages.of("en");
		var create = new CreateTaskFromVoiceService(sender, fileId -> new byte[] { 1, 2, 3 },
				new GroqTranscriptionClient(RestClients.create(stt.baseUrl(), sttSettings.connectTimeout(),
						sttSettings.readTimeout()), GROQ_KEY, sttSettings, new SingleRetry("Transcription", Duration.ZERO, d -> { })),
				new ChatCompletionsTaskParser(RestClients.create(llm.baseUrl(), llmSettings.connectTimeout(),
						llmSettings.readTimeout()), llmSettings, clock, zone, new SingleRetry("Task parser", Duration.ZERO, d -> { })),
				repository, new DraftStore(clock, Duration.ofMinutes(10)), messages, properties, clock, new TaskFactory(clock));
		commands = new IncomingCommandService(sender, messages, create,
				new ReminderActionService(repository, sender, messages, properties, clock),
				new ListTasksService(repository, sender, messages, properties, clock));
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

	private static void assertClean(String logs) {
		assertThat(logs).isNotBlank()
				.doesNotContain(TRANSCRIPT).doesNotContain("zebra").doesNotContain(TITLE)
				.doesNotContain(GROQ_KEY).doesNotContain(LLM_KEY)
				.doesNotContain(TestProperties.BOT_TOKEN);
	}

	@Test
	void aSuccessfulFlowLogsNoContentAtAnyLevel() {
		stt.reply(200, "{\"text\":\"" + TRANSCRIPT + "\"}");
		llm.reply(200, completion(PARSED));
		try (LogCapture logs = LogCapture.ofRootWithConfiguredLibraryLevels()) {
			commands.handle(IncomingCommandRequest.voice(CHAT, 42, 1, "file-1", 5));
			String data = sender.withButtons.get(0).buttons().get(0).get(0).callbackData();
			UUID draft = UUID.fromString(data.substring("task:ok:".length()));
			commands.handle(IncomingCommandRequest.callback(CHAT, 42, 2, "cb-1", "task:ok:" + draft));
			commands.handle(IncomingCommandRequest.text(CHAT, 42, 3, "/tasks"));
			String done = sender.withButtons.get(1).buttons().get(0).get(0).callbackData();
			commands.handle(IncomingCommandRequest.callback(CHAT, 42, 4, "cb-2", done));

			assertThat(repository.tasks).hasSize(1);
			assertClean(logs.allText());
		}
	}

	@Test
	void failingServicesThatEchoTheRequestLogOnlyKindsAndStatusCodes() {
		String echo = "{\"error\":\"could not process " + TRANSCRIPT + " with " + GROQ_KEY + " " + LLM_KEY + "\"}";
		stt.reply(500, echo).reply(500, echo);
		llm.reply(502, echo).reply(502, echo);
		try (LogCapture logs = LogCapture.ofRootWithConfiguredLibraryLevels()) {
			commands.handle(IncomingCommandRequest.voice(CHAT, 42, 1, "file-1", 5));
			commands.handle(IncomingCommandRequest.text(CHAT, 42, 2, TRANSCRIPT));

			assertThat(sender.texts).hasSize(2);
			assertClean(logs.allText());
			assertThat(logs.allText()).contains("HTTP 500").contains("HTTP 502");
		}
	}
}
