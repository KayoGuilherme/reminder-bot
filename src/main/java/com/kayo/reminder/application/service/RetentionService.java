package com.kayo.reminder.application.service;

import com.kayo.reminder.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionService {

	private final TaskService tasks;
	private final Duration retention;
	private final Clock clock;

	public RetentionService(TaskService tasks, AppProperties properties, Clock clock) {
		this.tasks = tasks;
		this.retention = Duration.ofDays(properties.retention().days());
		this.clock = clock;
	}

	public int deleteExpired() {
		Instant cutoff = clock.instant().minus(retention);
		return tasks.deleteFinishedBefore(cutoff);
	}
}
