package com.kayo.reminder.infrastructure.scheduling;

import com.kayo.reminder.application.port.in.CleanUpFinishedTasksUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The cleanup job exists only when {@code app.retention.enabled} is true (the default). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class RetentionConfiguration {

	@Bean
	@ConditionalOnBean(CleanUpFinishedTasksUseCase.class)
	RetentionScheduler retentionScheduler(CleanUpFinishedTasksUseCase useCase) {
		return new RetentionScheduler(useCase);
	}
}
