package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.config.AppProperties.Telegram.Retry;
import com.kayo.reminder.testsupport.LogCapture;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TelegramLogHygieneTest {

	@Test
	void aFailingTelegramSendLogsNeitherTheMessageTextNorTheToken() throws Exception {
		try (FakeTelegramApi api = new FakeTelegramApi()) {
			api.errorAlways(400, "Bad Request: chat not found");
			var sender = new TelegramMessageSender(api.client(),
					new Retry(2, Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofSeconds(1)),
					FakeTelegramApi.TOKEN, d -> { });
			try (LogCapture logs = LogCapture.ofRootWithConfiguredLibraryLevels()) {
				sender.sendText(100, "your task Buy zebra-milk (remember to buy zebra-milk) is due");
				sender.editText(100, 5, "Buy zebra-milk");

				// The JDK HTTP server inside the fake Telegram API logs the request lines it receives: not the app.
				String text = logs.allText(e -> !e.getLoggerName().startsWith("com.sun.net.httpserver"));
				assertThat(text).isNotBlank().doesNotContain("zebra").doesNotContain(FakeTelegramApi.TOKEN);
			}
		}
	}
}
