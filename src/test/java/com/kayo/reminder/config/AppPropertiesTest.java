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
			RetryProperties retry = props.telegram().retry();
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
	void schedulerDefaultsToEnabledEvery30Seconds() {
		runner().run(context -> {
			SchedulerProperties scheduler = context.getBean(AppProperties.class).scheduler();
			assertThat(scheduler.enabled()).isTrue();
			assertThat(scheduler.interval()).isEqualTo(Duration.ofSeconds(30));
		});
	}

	@Test
	void schedulerCanBeOverridden() {
		runner("app.scheduler.interval=1m", "app.scheduler.enabled=false").run(context -> {
			SchedulerProperties scheduler = context.getBean(AppProperties.class).scheduler();
			assertThat(scheduler.enabled()).isFalse();
			assertThat(scheduler.interval()).isEqualTo(Duration.ofMinutes(1));
		});
	}

	@Test
	void failsWhenSchedulerIntervalIsNotPositive() {
		runner("app.scheduler.interval=0s").run(context -> assertThat(context).hasFailed().getFailure()
				.hasStackTraceContaining("scheduler.interval"));
		runner("app.scheduler.interval=-5s").run(context -> assertThat(context).hasFailed().getFailure()
				.hasStackTraceContaining("scheduler.interval"));
	}

	@Test
	void reminderDefaultsToEveryTwoMinutesUpToTenRepeats() {
		runner().run(context -> {
			ReminderProperties reminder = context.getBean(AppProperties.class).reminder();
			assertThat(reminder.repeatInterval()).isEqualTo(Duration.ofMinutes(2));
			assertThat(reminder.maxRepeats()).isEqualTo(10);
		});
	}

	@Test
	void reminderCanBeOverridden() {
		runner("APP_REMINDER_REPEAT_INTERVAL=1m", "APP_REMINDER_MAX_REPEATS=3").run(context -> {
			ReminderProperties reminder = context.getBean(AppProperties.class).reminder();
			assertThat(reminder.repeatInterval()).isEqualTo(Duration.ofMinutes(1));
			assertThat(reminder.maxRepeats()).isEqualTo(3);
		});
	}

	@Test
	void failsWhenReminderRepeatIntervalIsNotPositive() {
		runner("APP_REMINDER_REPEAT_INTERVAL=0s").run(context -> assertThat(context).hasFailed().getFailure()
				.hasStackTraceContaining("reminder.repeatInterval"));
	}

	@Test
	void failsWhenReminderMaxRepeatsIsBelowOne() {
		runner("APP_REMINDER_MAX_REPEATS=0").run(context -> assertThat(context).hasFailed().getFailure()
				.hasStackTraceContaining("reminder.maxRepeats"));
	}

	@Test
	void secretsAreMaskedInToString() {
		runner("LLM_API_KEY=llm-secret").run(context -> {
			String text = context.getBean(AppProperties.class).toString();
			assertThat(text).doesNotContain("bot-token-secret", "groq-secret", "gemini-secret", "llm-secret");
		});
	}

	@Test
	void voiceTaskSettingsDefaults() {
		runner().run(context -> {
			AppProperties props = context.getBean(AppProperties.class);
			assertThat(props.stt().language()).isEmpty();
			assertThat(props.stt().model()).isEqualTo("whisper-large-v3-turbo");
			assertThat(props.stt().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
			assertThat(props.stt().readTimeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(props.llm().baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
			assertThat(props.llm().model()).isNotBlank();
			assertThat(props.voice().maxSeconds()).isEqualTo(60);
			assertThat(props.draft().ttl()).isEqualTo(Duration.ofMinutes(10));
		});
	}

	@Test
	void sttLanguageCanBeOverridden() {
		runner("APP_STT_LANGUAGE=en").run(context ->
				assertThat(context.getBean(AppProperties.class).stt().language()).isEqualTo("en"));
	}

	@Test
	void llmKeyFallsBackToGroqKeyAndCanPointAtGemini() {
		runner().run(context ->
				assertThat(context.getBean(AppProperties.class).llm().apiKey()).isEqualTo("groq-secret"));
		runner("LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai", "LLM_MODEL=gemini-x",
				"LLM_API_KEY=gem-key").run(context -> {
					LlmProperties llm = context.getBean(AppProperties.class).llm();
					assertThat(llm.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai");
					assertThat(llm.model()).isEqualTo("gemini-x");
					assertThat(llm.apiKey()).isEqualTo("gem-key");
				});
	}

	@Test
	void failsWhenLlmKeyBlank() {
		runner("LLM_API_KEY=").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("llm.apiKey"));
	}

	@Test
	void failsWhenVoiceOrDraftSettingsNotPositive() {
		runner("APP_VOICE_MAX_SECONDS=0").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("voice.maxSeconds"));
		runner("APP_DRAFT_TTL=0s").run(context -> assertThat(context).hasFailed());
		runner("app.llm.read-timeout=0s").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void retentionResilienceShutdownAndTelegramTimeoutsHaveDefaults() {
		runner().run(context -> {
			AppProperties props = context.getBean(AppProperties.class);
			assertThat(props.retention().enabled()).isTrue();
			assertThat(props.retention().days()).isEqualTo(30);
			assertThat(props.resilience().retryBackoff()).isEqualTo(Duration.ofMillis(500));
			assertThat(props.shutdown().timeout()).isEqualTo(Duration.ofSeconds(30));
			assertThat(props.telegram().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
			assertThat(props.telegram().readTimeout()).isEqualTo(Duration.ofSeconds(30));
		});
	}

	@Test
	void retentionResilienceShutdownAndTelegramTimeoutsCanBeOverridden() {
		runner("APP_RETENTION_DAYS=90", "APP_RETENTION_ENABLED=false", "APP_RETRY_BACKOFF=2s",
				"APP_SHUTDOWN_TIMEOUT=10s", "APP_TELEGRAM_CONNECT_TIMEOUT=1s", "APP_TELEGRAM_READ_TIMEOUT=10s")
				.run(context -> {
					AppProperties props = context.getBean(AppProperties.class);
					assertThat(props.retention().enabled()).isFalse();
					assertThat(props.retention().days()).isEqualTo(90);
					assertThat(props.resilience().retryBackoff()).isEqualTo(Duration.ofSeconds(2));
					assertThat(props.shutdown().timeout()).isEqualTo(Duration.ofSeconds(10));
					assertThat(props.telegram().connectTimeout()).isEqualTo(Duration.ofSeconds(1));
					assertThat(props.telegram().readTimeout()).isEqualTo(Duration.ofSeconds(10));
				});
	}

	@Test
	void failsWhenRetentionResilienceShutdownOrTelegramTimeoutsInvalid() {
		runner("APP_RETENTION_DAYS=0").run(context -> assertThat(context)
				.hasFailed().getFailure().hasStackTraceContaining("retention.days"));
		runner("APP_RETRY_BACKOFF=0s").run(context -> assertThat(context)
				.hasFailed().getFailure().hasStackTraceContaining("resilience.retryBackoff"));
		runner("APP_SHUTDOWN_TIMEOUT=-5s").run(context -> assertThat(context)
				.hasFailed().getFailure().hasStackTraceContaining("shutdown.timeout"));
		runner("APP_TELEGRAM_CONNECT_TIMEOUT=0s").run(context -> assertThat(context)
				.hasFailed().getFailure().hasStackTraceContaining("telegram.connectTimeout"));
		runner("APP_TELEGRAM_READ_TIMEOUT=0s").run(context -> assertThat(context)
				.hasFailed().getFailure().hasStackTraceContaining("telegram.readTimeout"));
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class Config {
	}
}
