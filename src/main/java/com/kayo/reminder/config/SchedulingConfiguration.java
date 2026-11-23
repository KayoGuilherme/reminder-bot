package com.kayo.reminder.config;

import com.kayo.reminder.application.component.ReminderScheduler;
import com.kayo.reminder.application.service.SendDueRemindersService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfiguration {

	@Bean
	@ConditionalOnBean(SendDueRemindersService.class)
	ReminderScheduler reminderScheduler(SendDueRemindersService reminders) {
		return new ReminderScheduler(reminders);
	}
}
