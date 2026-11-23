package com.kayo.reminder.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZoneId;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	@NotNull
	private final ZoneId timezone;

	@NotNull
	@Pattern(regexp = "en|pt-BR", message = "must be 'en' or 'pt-BR'")
	private final String language;

	@NotNull
	@Valid
	private final TelegramProperties telegram;

	@NotNull
	@Valid
	private final GroqProperties groq;

	@NotNull
	@Valid
	private final GeminiProperties gemini;

	@Valid
	private final SttProperties stt;

	@NotNull
	@Valid
	private final LlmProperties llm;

	@Valid
	private final VoiceProperties voice;

	@Valid
	private final DraftProperties draft;

	@Valid
	private final SchedulerProperties scheduler;

	@Valid
	private final ReminderProperties reminder;

	@Valid
	private final RetentionProperties retention;

	@Valid
	private final ResilienceProperties resilience;

	@Valid
	private final ShutdownProperties shutdown;

	public AppProperties(
			ZoneId timezone,
			@DefaultValue("en") String language,
			TelegramProperties telegram,
			GroqProperties groq,
			GeminiProperties gemini,
			@DefaultValue SttProperties stt,
			LlmProperties llm,
			@DefaultValue VoiceProperties voice,
			@DefaultValue DraftProperties draft,
			@DefaultValue SchedulerProperties scheduler,
			@DefaultValue ReminderProperties reminder,
			@DefaultValue RetentionProperties retention,
			@DefaultValue ResilienceProperties resilience,
			@DefaultValue ShutdownProperties shutdown) {
		this.timezone = timezone;
		this.language = language;
		this.telegram = telegram;
		this.groq = groq;
		this.gemini = gemini;
		this.stt = stt;
		this.llm = llm;
		this.voice = voice;
		this.draft = draft;
		this.scheduler = scheduler;
		this.reminder = reminder;
		this.retention = retention;
		this.resilience = resilience;
		this.shutdown = shutdown;
	}
}
