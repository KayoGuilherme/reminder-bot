package com.kayo.reminder.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class ReminderProperties {

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration repeatInterval;

	@Min(1)
	private final int maxRepeats;

	public ReminderProperties(@DefaultValue("2m") Duration repeatInterval, @DefaultValue("10") int maxRepeats) {
		this.repeatInterval = repeatInterval;
		this.maxRepeats = maxRepeats;
	}
}
