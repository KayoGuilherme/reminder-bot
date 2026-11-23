package com.kayo.reminder.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class GeminiProperties {

	@NotBlank
	private final String apiKey;

	public GeminiProperties(String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public String toString() {
		return "GeminiProperties[apiKey=***]";
	}
}
