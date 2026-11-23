package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.OwnedTaskLookup;
import com.kayo.reminder.application.component.TaskViewFactory;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SnoozeTaskService {

	private static final Logger log = LoggerFactory.getLogger(SnoozeTaskService.class);

	private final TaskService tasks;
	private final OwnedTaskLookup lookup;
	private final TaskViewFactory views;
	private final Clock clock;

	public SnoozeTaskService(TaskService tasks, OwnedTaskLookup lookup, TaskViewFactory views, Clock clock) {
		this.tasks = tasks;
		this.lookup = lookup;
		this.views = views;
		this.clock = clock;
	}

	public TaskViewResponse snooze(String id) {
		TaskResponse task = lookup.find(id);
		Optional.of(task).filter(candidate -> candidate.status() != TaskStatus.PENDING)
				.ifPresent(refuse(TaskFailure.TASK_NOT_PENDING));
		Optional.of(task).filter(candidate -> candidate.lastReminderStage() == 0)
				.ifPresent(refuse(TaskFailure.NOT_SNOOZABLE));
		Instant now = clock.instant();
		Instant newDeadline = Stream.of(task.deadlineAt(), now).max(Comparator.naturalOrder()).orElseThrow()
				.plus(ReminderPolicy.SNOOZE);
		return Optional.of(tasks.snooze(task.id(), task.deadlineAt(), newDeadline, now)).filter(Boolean::booleanValue)
				.flatMap(applied -> tasks.findById(task.id())).map(this::snoozed)
				.orElseGet(() -> lost(task.id()));
	}

	private TaskViewResponse snoozed(TaskResponse task) {
		log.info("Task {} snoozed", task.id());
		return views.create(task);
	}

	private TaskViewResponse lost(UUID taskId) {
		throw new TaskActionException(tasks.findById(taskId).filter(current -> current.status() == TaskStatus.PENDING)
				.map(current -> TaskFailure.NOT_SNOOZABLE).orElse(TaskFailure.TASK_NOT_PENDING));
	}

	private static Consumer<TaskResponse> refuse(TaskFailure failure) {
		return task -> {
			throw new TaskActionException(failure);
		};
	}
}
