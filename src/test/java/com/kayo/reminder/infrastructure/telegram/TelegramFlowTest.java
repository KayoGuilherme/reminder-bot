package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.service.IncomingCommandService;
import com.kayo.reminder.application.service.Messages;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.config.AppProperties.Telegram.Retry;
import com.kayo.reminder.testsupport.LogCapture;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** The acceptance criteria end to end, minus Spring and the real network: update in, HTTP call out. */
class TelegramFlowTest {

	private FakeTelegramApi api;

	@BeforeEach
	void start() throws Exception {
		api = new FakeTelegramApi();
	}

	@AfterEach
	void stop() {
		api.close();
	}

	private TelegramUpdateConsumer consumer(String language, UnaryOperator<HandleIncomingCommandUseCase> decorate) {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		Retry retry = new Retry(3, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofSeconds(1));
		AppProperties props = new AppProperties(ZoneId.of("UTC"), language,
				new AppProperties.Telegram(FakeTelegramApi.TOKEN, 42L, true, retry),
				new AppProperties.Groq("g"), new AppProperties.Gemini("m"));
		IncomingCommandService service = new IncomingCommandService(
				new TelegramMessageSender(api.client(), retry, FakeTelegramApi.TOKEN, d -> { }),
				new Messages(source, props));
		return new TelegramUpdateConsumer(new UpdateAllowlist(42), new TelegramUpdateMapper(), decorate.apply(service));
	}

	@Test
	void startFromAllowedAccountIsAnswered() {
		api.sentMessage(1);
		consumer("en", UnaryOperator.identity()).consume(List.of(Updates.text(1, 42, 100, "/start")));
		assertThat(api.requests()).hasSize(1);
		assertThat(api.requests().get(0).path()).endsWith("/sendmessage");
		assertThat(api.requests().get(0).body()).contains("\"chat_id\":\"100\"").contains("task reminder bot");
	}

	@Test
	void portugueseReplyWhenConfigured() {
		api.sentMessage(1);
		consumer("pt-BR", UnaryOperator.identity()).consume(List.of(Updates.text(1, 42, 100, "/help")));
		assertThat(api.requests().get(0).body()).contains("bot de lembretes");
	}

	@Test
	void anyOtherAccountGetsNothing() {
		try (LogCapture logs = new LogCapture(UpdateAllowlist.class)) {
			consumer("en", UnaryOperator.identity()).consume(List.of(
					Updates.text(1, 99, 99, "/start"),
					Updates.voice(2, 99, 99, "f", 3),
					Updates.callback(3, 99, 99, 5, "cb", "x")));
			assertThat(api.requests()).isEmpty();
			assertThat(logs.events(Level.WARN)).hasSize(3);
			assertThat(logs.allText()).doesNotContain("/start");
		}
	}

	@Test
	void exceptionInHandlerDoesNotStopTheBot() {
		api.sentMessage(1);
		boolean[] first = { true };
		TelegramUpdateConsumer consumer = consumer("en", real -> command -> {
			if (first[0]) {
				first[0] = false;
				throw new IllegalStateException("handler blew up");
			}
			real.handle(command);
		});
		try (LogCapture logs = new LogCapture(TelegramUpdateConsumer.class)) {
			consumer.consume(List.of(Updates.text(1, 42, 100, "boom"), Updates.text(2, 42, 100, "/start")));
			assertThat(logs.events(Level.ERROR)).hasSize(1);
			assertThat(logs.allText()).doesNotContain("boom");
		}
		assertThat(api.requests()).hasSize(1);
		assertThat(api.requests().get(0).body()).contains("task reminder bot");
	}
}
