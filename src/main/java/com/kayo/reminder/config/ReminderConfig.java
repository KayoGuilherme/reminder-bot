package com.kayo.reminder.config;

import com.kayo.reminder.domain.ReminderPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The single reminder policy, built from {@code app.reminder.*}, so the stage numbers live in one place. */
@Configuration
public class ReminderConfig {

	@Bean
	public ReminderPolicy reminderPolicy(AppProperties properties) {
		return new ReminderPolicy(properties.reminder().repeatInterval(), properties.reminder().maxRepeats());
	}
}
