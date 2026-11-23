package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.testsupport.FakeHttpServer;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Duration;
import java.time.ZoneId;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

class TelegramConfigurationTest {

	private static AppProperties properties(Duration connect, Duration read) {
		AppProperties base = TestProperties.of(ZoneId.of("UTC"), "en");
		AppProperties.Telegram telegram = base.telegram();
		return new AppProperties(base.timezone(), base.language(),
				new AppProperties.Telegram(telegram.botToken(), telegram.allowedUserId(), true, telegram.retry(),
						connect, read),
				base.groq(), base.gemini(), base.stt(), base.llm(), base.voice(), base.draft(), base.scheduler(),
				base.reminder(), base.retention(), base.resilience(), base.shutdown());
	}

	@Test
	void theSendClientUsesTheConfiguredTimeouts() {
		OkHttpClient client = TelegramConfiguration.sendClient(properties(Duration.ofSeconds(2), Duration.ofSeconds(10)));

		assertThat(client.connectTimeoutMillis()).isEqualTo(2_000);
		assertThat(client.readTimeoutMillis()).isEqualTo(10_000);
	}

	@Test
	void theLongPollClientIsBoundedAboveThePollTimeoutAndConnectsWithTheConfiguredTimeout() {
		OkHttpClient client = TelegramConfiguration.longPollClient(properties(Duration.ofSeconds(3), Duration.ofSeconds(10)));

		assertThat(client.connectTimeoutMillis()).isEqualTo(3_000);
		assertThat(client.readTimeoutMillis()).isEqualTo(75_000).isGreaterThan(50_000);
	}

	@Test
	void aSendThatGetsNoAnswerFailsAfterTheReadTimeout() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer()) {
			server.replySlowly(200, "{}", 5_000);
			OkHttpClient http = TelegramConfiguration.sendClient(properties(Duration.ofSeconds(2), Duration.ofMillis(300)));
			TelegramUrl url = TelegramUrl.builder().schema("http").host("127.0.0.1")
					.port(Integer.parseInt(server.baseUrl().substring(server.baseUrl().lastIndexOf(':') + 1))).build();
			OkHttpTelegramClient client = new OkHttpTelegramClient(http, "token", url);

			long start = System.nanoTime();
			assertThatThrownBy(() -> client.execute(new SendMessage("1", "hello"))).isInstanceOf(TelegramApiException.class);
			Duration waited = Duration.ofNanos(System.nanoTime() - start);

			assertThat(waited).isLessThan(Duration.ofSeconds(3));
		}
	}
}
