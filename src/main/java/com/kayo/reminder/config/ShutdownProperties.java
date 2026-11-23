package com.kayo.reminder.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class ShutdownProperties {

	@NotNull
	@DurationMin(nanos = 1)
	private final Duration timeout;

	public ShutdownProperties(@DefaultValue("30s") Duration timeout) {
		this.timeout = timeout;
	}
}
