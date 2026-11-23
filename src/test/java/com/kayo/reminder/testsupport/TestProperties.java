package com.kayo.reminder.testsupport;

import com.kayo.reminder.config.AppProperties;
import java.time.Duration;
import java.time.ZoneId;

/** Builds {@link AppProperties} for unit tests that do not start Spring. */
public final class TestProperties {

	public static final String BOT_TOKEN = "SECRET-TOKEN-123";

	private TestProperties() {
	}

	public static AppProperties of(ZoneId zone, String language) {
		return of(zone, language, 60, Duration.ofMinutes(10));
	}

	public static AppProperties of(ZoneId zone, String language, int maxVoiceSeconds, Duration draftTtl) {
		return new AppProperties(zone, language,
				new AppProperties.Telegram(BOT_TOKEN, 42L, true,
						new AppProperties.Telegram.Retry(3, Duration.ofMillis(1), Duration.ofMillis(2),
								Duration.ofSeconds(1)),
						Duration.ofSeconds(5), Duration.ofSeconds(30)),
				new AppProperties.Groq("groq-key"), new AppProperties.Gemini("gemini-key"),
				new AppProperties.Stt("pt", "whisper-large-v3-turbo", "https://api.groq.com/openai/v1",
						Duration.ofSeconds(5), Duration.ofSeconds(30)),
				new AppProperties.Llm("https://api.groq.com/openai/v1", "test-model", "llm-key",
						Duration.ofSeconds(5), Duration.ofSeconds(30)),
				new AppProperties.Voice(maxVoiceSeconds), new AppProperties.Draft(draftTtl),
				new AppProperties.Scheduler(true, Duration.ofSeconds(30)),
				new AppProperties.Reminder(Duration.ofMinutes(2), 10),
				new AppProperties.Retention(true, 30),
				new AppProperties.Resilience(Duration.ofMillis(500)),
				new AppProperties.Shutdown(Duration.ofSeconds(30)));
	}
}
