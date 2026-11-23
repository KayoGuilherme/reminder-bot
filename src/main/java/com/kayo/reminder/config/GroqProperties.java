package com.kayo.reminder.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class GroqProperties {

	@NotBlank
	private final String apiKey;

	public GroqProperties(String apiKey) {
		this.apiKey = apiKey;
	}

	@Override
	public String toString() {
		return "GroqProperties[apiKey=***]";
	}
}
