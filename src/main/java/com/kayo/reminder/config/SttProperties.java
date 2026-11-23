package com.kayo.reminder.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class SttProperties {

	private final String language;

	@NotBlank
	private final String model;

	@NotBlank
	private final String baseUrl;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration connectTimeout;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration readTimeout;

	public SttProperties(
			@DefaultValue("") String language,
			@DefaultValue("whisper-large-v3-turbo") String model,
			@DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
			@DefaultValue("5s") Duration connectTimeout,
			@DefaultValue("30s") Duration readTimeout) {
		this.language = language;
		this.model = model;
		this.baseUrl = baseUrl;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}
}
