package com.kayo.reminder.infrastructure.scheduling;

import com.kayo.reminder.application.port.in.SendDueRemindersUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The reminder job exists only when {@code app.scheduler.enabled} is true (the default). It also needs the use case,
 * which is absent when the Telegram adapter is turned off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfiguration {

	@Bean
	@ConditionalOnBean(SendDueRemindersUseCase.class)
	ReminderScheduler reminderScheduler(SendDueRemindersUseCase useCase) {
		return new ReminderScheduler(useCase);
	}
}
