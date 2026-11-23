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
import java.util.UUID;
import java.util.function.BiPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FinishTaskService {

	private static final Logger log = LoggerFactory.getLogger(FinishTaskService.class);

	private final TaskService tasks;
	private final OwnedTaskLookup lookup;
	private final TaskViewFactory views;
	private final Clock clock;

	public FinishTaskService(TaskService tasks, OwnedTaskLookup lookup, TaskViewFactory views, Clock clock) {
		this.tasks = tasks;
		this.lookup = lookup;
		this.views = views;
		this.clock = clock;
	}

	public TaskViewResponse complete(String id) {
		return finish(id, TaskStatus.DONE, tasks::markDone);
	}

	public TaskViewResponse cancel(String id) {
		return finish(id, TaskStatus.CANCELED, tasks::markCanceled);
	}

	private TaskViewResponse finish(String id, TaskStatus target, BiPredicate<UUID, Instant> apply) {
		UUID taskId = lookup.find(id).id();
		apply.test(taskId, clock.instant());
		TaskResponse current = tasks.findById(taskId).filter(task -> task.status() == target)
				.orElseThrow(() -> new TaskActionException(TaskFailure.TASK_NOT_PENDING));
		log.info("Task {} is {}", taskId, target);
		return views.create(current);
	}
}
