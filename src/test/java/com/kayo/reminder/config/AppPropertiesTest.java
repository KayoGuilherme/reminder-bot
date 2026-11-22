package com.kayo.reminder.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppPropertiesTest {

	private static final String[] VALID = {
			"TELEGRAM_BOT_TOKEN=bot-token-secret",
			"TELEGRAM_ALLOWED_USER_ID=12345",
			"GROQ_API_KEY=groq-secret",
			"GEMINI_API_KEY=gemini-secret"
	};

	private ApplicationContextRunner runner(String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(Config.class)
				.withPropertyValues(VALID)
				.withPropertyValues(overrides);
	}

	@Test
	void bindsValidValuesAndAppliesDefaultTimezone() {
		runner().run(context -> {
			AppProperties props = context.getBean(AppProperties.class);
			assertThat(props.timezone()).isEqualTo(ZoneId.of("America/Fortaleza"));
			assertThat(props.telegram().allowedUserId()).isEqualTo(12345L);
			assertThat(props.telegram().botToken()).isEqualTo("bot-token-secret");
		});
	}

	@Test
	void timezoneCanBeOverridden() {
		runner("APP_TIMEZONE=UTC").run(context ->
				assertThat(context.getBean(AppProperties.class).timezone()).isEqualTo(ZoneId.of("UTC")));
	}

	@Test
	void failsWhenBotTokenMissing() {
		runner("TELEGRAM_BOT_TOKEN=").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("telegram.botToken"));
	}

	@Test
	void failsWhenApiKeyBlank() {
		runner("GROQ_API_KEY=  ").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("groq.apiKey"));
		runner("GEMINI_API_KEY=").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("gemini.apiKey"));
	}

	@Test
	void failsWhenUserIdMissingOrNotNumeric() {
		runner("TELEGRAM_ALLOWED_USER_ID=").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("telegram.allowedUserId"));
		runner("TELEGRAM_ALLOWED_USER_ID=abc").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("app.telegram.allowed-user-id"));
	}

	@Test
	void failsWhenTimezoneInvalid() {
		runner("APP_TIMEZONE=Not/AZone").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("app.timezone"));
	}

	@Test
	void languageAndTelegramSwitchDefaults() {
		runner().run(context -> {
			AppProperties props = context.getBean(AppProperties.class);
			assertThat(props.language()).isEqualTo("en");
			assertThat(props.telegram().enabled()).isTrue();
			AppProperties.Telegram.Retry retry = props.telegram().retry();
			assertThat(retry.maxAttempts()).isEqualTo(3);
			assertThat(retry.initialBackoff()).isEqualTo(Duration.ofMillis(500));
			assertThat(retry.maxBackoff()).isEqualTo(Duration.ofSeconds(5));
			assertThat(retry.maxRetryAfter()).isEqualTo(Duration.ofSeconds(30));
		});
	}

	@Test
	void bindsPortugueseAndDisabledTelegram() {
		runner("APP_LANGUAGE=pt-BR", "APP_TELEGRAM_ENABLED=false").run(context -> {
			AppProperties props = context.getBean(AppProperties.class);
			assertThat(props.language()).isEqualTo("pt-BR");
			assertThat(props.telegram().enabled()).isFalse();
		});
	}

	@Test
	void failsWhenLanguageUnsupported() {
		runner("APP_LANGUAGE=fr").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("language"));
	}

	@Test
	void failsWhenTelegramEnabledIsNotBoolean() {
		runner("APP_TELEGRAM_ENABLED=maybe").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("app.telegram.enabled"));
	}

	@Test
	void failsWhenRetrySettingsInvalid() {
		runner("app.telegram.retry.max-attempts=0").run(context -> assertThat(context).hasFailed());
		runner("app.telegram.retry.initial-backoff=0s").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void secretsAreMaskedInToString() {
		runner().run(context -> {
			String text = context.getBean(AppProperties.class).toString();
			assertThat(text).doesNotContain("bot-token-secret", "groq-secret", "gemini-secret");
		});
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class Config {
	}
}
