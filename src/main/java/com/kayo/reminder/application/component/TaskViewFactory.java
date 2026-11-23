package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class TaskViewFactory {

	private final ZoneId zone;
	private final Clock clock;

	public TaskViewFactory(AppProperties properties, Clock clock) {
		this.zone = properties.timezone();
		this.clock = clock;
	}

	public TaskViewResponse create(TaskResponse task) {
		return new TaskViewResponse(task.id(), task.title(), task.urgency(), task.status(), task.language(),
				task.startAt(), task.deadlineAt(), task.deadlineAt().atZone(zone).toOffsetDateTime(), overdue(task));
	}

	private boolean overdue(TaskResponse task) {
		return task.status() == TaskStatus.PENDING && !task.deadlineAt().isAfter(clock.instant());
	}
}
