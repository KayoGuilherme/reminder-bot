package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.testsupport.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelegramStartupCheckTest {

	private FakeTelegramApi api;

	@BeforeEach
	void start() throws Exception {
		api = new FakeTelegramApi();
	}

	@AfterEach
	void stop() {
		api.close();
	}

	@Test
	void anInvalidTokenIsOneClearErrorNamingTheSettingWithoutTheToken() {
		api.error(401, "Unauthorized");
		try (LogCapture logs = new LogCapture(TelegramStartupCheck.class)) {
			new TelegramStartupCheck(api.client()).run();

			assertThat(logs.events(Level.ERROR)).hasSize(1);
			assertThat(logs.allText()).contains("TELEGRAM_BOT_TOKEN").contains("cannot receive messages")
					.doesNotContain(FakeTelegramApi.TOKEN);
			assertThat(logs.events(Level.WARN)).isEmpty();
		}
	}

	@Test
	void aValidTokenLogsTheBotUsernameAndNoError() {
		api.reply(200, "{\"ok\":true,\"result\":{\"id\":1,\"is_bot\":true,\"first_name\":\"Bot\",\"username\":\"my_bot\"}}");
		try (LogCapture logs = new LogCapture(TelegramStartupCheck.class)) {
			new TelegramStartupCheck(api.client()).run();

			assertThat(logs.events(Level.INFO)).hasSize(1);
			assertThat(logs.allText()).contains("@my_bot").doesNotContain(FakeTelegramApi.TOKEN);
			assertThat(logs.events(Level.ERROR)).isEmpty();
			assertThat(logs.events(Level.WARN)).isEmpty();
		}
		assertThat(api.requests()).hasSize(1);
		assertThat(api.requests().get(0).path()).endsWith("/getme");
	}

	@Test
	void anUnreachableTelegramIsOnlyAWarningNotAnInvalidTokenError() {
		var client = api.client();
		api.close();
		try (LogCapture logs = new LogCapture(TelegramStartupCheck.class)) {
			new TelegramStartupCheck(client).run();

			assertThat(logs.events(Level.WARN)).hasSize(1);
			assertThat(logs.events(Level.ERROR)).isEmpty();
			assertThat(logs.allText()).doesNotContain("invalid").doesNotContain(FakeTelegramApi.TOKEN);
		}
	}

	@Test
	void aServerErrorIsAWarningToo() {
		api.error(502, "Bad Gateway");
		try (LogCapture logs = new LogCapture(TelegramStartupCheck.class)) {
			new TelegramStartupCheck(api.client()).run();

			assertThat(logs.events(Level.WARN)).hasSize(1);
			assertThat(logs.events(Level.ERROR)).isEmpty();
		}
	}
}
