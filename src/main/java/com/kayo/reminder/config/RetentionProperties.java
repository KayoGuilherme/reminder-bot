package com.kayo.reminder.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class RetentionProperties {

	private final boolean enabled;

	@Min(1)
	private final int days;

	public RetentionProperties(@DefaultValue("true") boolean enabled, @DefaultValue("30") int days) {
		this.enabled = enabled;
		this.days = days;
	}
}
