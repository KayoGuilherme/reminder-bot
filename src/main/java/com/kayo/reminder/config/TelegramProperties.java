package com.kayo.reminder.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class TelegramProperties {

	@NotBlank
	private final String botToken;

	@NotNull
	private final Long allowedUserId;

	private final boolean enabled;

	@Valid
	private final RetryProperties retry;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration connectTimeout;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration readTimeout;

	public TelegramProperties(
			String botToken,
			Long allowedUserId,
			@DefaultValue("true") boolean enabled,
			@DefaultValue RetryProperties retry,
			@DefaultValue("5s") Duration connectTimeout,
			@DefaultValue("30s") Duration readTimeout) {
		this.botToken = botToken;
		this.allowedUserId = allowedUserId;
		this.enabled = enabled;
		this.retry = retry;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	@Override
	public String toString() {
		return "TelegramProperties[botToken=***, allowedUserId=" + allowedUserId + ", enabled=" + enabled + "]";
	}
}
