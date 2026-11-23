package com.kayo.reminder.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class SchedulerProperties {

	private final boolean enabled;

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration interval;

	public SchedulerProperties(@DefaultValue("true") boolean enabled, @DefaultValue("30s") Duration interval) {
		this.enabled = enabled;
		this.interval = interval;
	}
}
