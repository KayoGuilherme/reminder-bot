package com.kayo.reminder.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class LlmProperties {

	@NotBlank
	private final String baseUrl;

	@NotBlank
	private final String model;

	@NotBlank
	private final String apiKey;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration connectTimeout;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration readTimeout;

	public LlmProperties(
			String baseUrl,
			String model,
			String apiKey,
			@DefaultValue("5s") Duration connectTimeout,
			@DefaultValue("30s") Duration readTimeout) {
		this.baseUrl = baseUrl;
		this.model = model;
		this.apiKey = apiKey;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	@Override
	public String toString() {
		return "LlmProperties[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=***]";
	}
}
