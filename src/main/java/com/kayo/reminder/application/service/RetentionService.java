package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.CleanUpFinishedTasksUseCase;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * A finished task's age counts from its last change (when it was marked done or canceled), and a task exactly
 * {@code days} old is kept. Nothing about the tasks, only the count, is ever reported.
 */
@Service
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionService implements CleanUpFinishedTasksUseCase {

	private final TaskRepository tasks;
	private final Duration retention;
	private final Clock clock;

	public RetentionService(TaskRepository tasks, AppProperties properties, Clock clock) {
		this.tasks = tasks;
		this.retention = Duration.ofDays(properties.retention().days());
		this.clock = clock;
	}

	@Override
	public int deleteExpired() {
		Instant cutoff = clock.instant().minus(retention);
		return tasks.deleteFinishedBefore(cutoff);
	}
}
