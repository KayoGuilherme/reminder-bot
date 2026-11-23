package com.kayo.reminder.config;

import com.kayo.reminder.application.component.RetentionScheduler;
import com.kayo.reminder.application.service.RetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class RetentionConfiguration {

	@Bean
	@ConditionalOnBean(RetentionService.class)
	RetentionScheduler retentionScheduler(RetentionService retention) {
		return new RetentionScheduler(retention);
	}
}
