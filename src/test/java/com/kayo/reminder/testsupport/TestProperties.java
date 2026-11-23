package com.kayo.reminder.testsupport;

import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.config.DraftProperties;
import com.kayo.reminder.config.GeminiProperties;
import com.kayo.reminder.config.GroqProperties;
import com.kayo.reminder.config.LlmProperties;
import com.kayo.reminder.config.ReminderProperties;
import com.kayo.reminder.config.ResilienceProperties;
import com.kayo.reminder.config.RetentionProperties;
import com.kayo.reminder.config.RetryProperties;
import com.kayo.reminder.config.SchedulerProperties;
import com.kayo.reminder.config.ShutdownProperties;
import com.kayo.reminder.config.SttProperties;
import com.kayo.reminder.config.TelegramProperties;
import com.kayo.reminder.config.VoiceProperties;
import java.time.Duration;
import java.time.ZoneId;

public final class TestProperties {

	public static final String BOT_TOKEN = "SECRET-TOKEN-123";

	private TestProperties() {
	}

	public static AppProperties of(ZoneId zone, String language) {
		return of(zone, language, 60, Duration.ofMinutes(10));
	}

	public static AppProperties of(ZoneId zone, String language, int maxVoiceSeconds, Duration draftTtl) {
		return new AppProperties(zone, language,
				new TelegramProperties(BOT_TOKEN, 42L, true,
						new RetryProperties(3, Duration.ofMillis(1), Duration.ofMillis(2),
								Duration.ofSeconds(1)),
						Duration.ofSeconds(5), Duration.ofSeconds(30)),
				new GroqProperties("groq-key"), new GeminiProperties("gemini-key"),
				new SttProperties("pt", "whisper-large-v3-turbo", "https://api.groq.com/openai/v1",
						Duration.ofSeconds(5), Duration.ofSeconds(30)),
				new LlmProperties("https://api.groq.com/openai/v1", "test-model", "llm-key",
						Duration.ofSeconds(5), Duration.ofSeconds(30)),
				new VoiceProperties(maxVoiceSeconds), new DraftProperties(draftTtl),
				new SchedulerProperties(true, Duration.ofSeconds(30)),
				new ReminderProperties(Duration.ofMinutes(2), 10),
				new RetentionProperties(true, 30),
				new ResilienceProperties(Duration.ofMillis(500)),
				new ShutdownProperties(Duration.ofSeconds(30)));
	}
}
