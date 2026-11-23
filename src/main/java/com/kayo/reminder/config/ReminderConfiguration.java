package com.kayo.reminder.config;

import com.kayo.reminder.application.service.ReminderPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReminderConfiguration {

	@Bean
	public ReminderPolicy reminderPolicy(AppProperties properties) {
		return new ReminderPolicy(properties.reminder().repeatInterval(), properties.reminder().maxRepeats());
	}
}
