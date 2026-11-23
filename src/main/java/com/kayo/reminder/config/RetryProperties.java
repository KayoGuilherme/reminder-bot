package com.kayo.reminder.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class RetryProperties {

	@Min(1)
	private final int maxAttempts;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration initialBackoff;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration maxBackoff;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration maxRetryAfter;

	public RetryProperties(
			@DefaultValue("3") int maxAttempts,
			@DefaultValue("500ms") Duration initialBackoff,
			@DefaultValue("5s") Duration maxBackoff,
			@DefaultValue("30s") Duration maxRetryAfter) {
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
		this.maxBackoff = maxBackoff;
		this.maxRetryAfter = maxRetryAfter;
	}
}
