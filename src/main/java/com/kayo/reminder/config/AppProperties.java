package com.kayo.reminder.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.ZoneId;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@NotNull ZoneId timezone,
		@DefaultValue("en") @NotNull @Pattern(regexp = "en|pt-BR", message = "must be 'en' or 'pt-BR'") String language,
		@NotNull @Valid Telegram telegram,
		@NotNull @Valid Groq groq,
		@NotNull @Valid Gemini gemini) {

	public record Telegram(
			@NotBlank String botToken,
			@NotNull Long allowedUserId,
			@DefaultValue("true") boolean enabled,
			@DefaultValue @Valid Retry retry) {

		@Override
		public String toString() {
			return "Telegram[botToken=***, allowedUserId=" + allowedUserId + ", enabled=" + enabled + "]";
		}

		/** Outbound retry policy: attempts include the first call; {@code maxRetryAfter} caps a 429 wait. */
		public record Retry(
				@DefaultValue("3") @Min(1) int maxAttempts,
				@DefaultValue("500ms") @NotNull @DurationMin(nanos = 1) Duration initialBackoff,
				@DefaultValue("5s") @NotNull @DurationMin(nanos = 1) Duration maxBackoff,
				@DefaultValue("30s") @NotNull @DurationMin(nanos = 1) Duration maxRetryAfter) {
		}
	}

	public record Groq(@NotBlank String apiKey) {

		@Override
		public String toString() {
			return "Groq[apiKey=***]";
		}
	}

	public record Gemini(@NotBlank String apiKey) {

		@Override
		public String toString() {
			return "Gemini[apiKey=***]";
		}
	}
}
