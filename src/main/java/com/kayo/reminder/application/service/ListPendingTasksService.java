package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.TaskViewFactory;
import com.kayo.reminder.application.dto.response.PendingTasksResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ListPendingTasksService {

	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 50;

	private static final Logger log = LoggerFactory.getLogger(ListPendingTasksService.class);

	private final TaskService tasks;
	private final TaskViewFactory views;
	private final long ownerChatId;

	public ListPendingTasksService(TaskService tasks, TaskViewFactory views, AppProperties properties) {
		this.tasks = tasks;
		this.views = views;
		this.ownerChatId = properties.telegram().allowedUserId();
	}

	public PendingTasksResponse list(Integer limit) {
		int size = Optional.ofNullable(limit).orElse(DEFAULT_LIMIT);
		Optional.of(size).filter(value -> value >= 1 && value <= MAX_LIMIT)
				.orElseThrow(() -> new TaskActionException(TaskFailure.INVALID_LIMIT));
		PendingTasksResponse pending = new PendingTasksResponse(
				tasks.findPendingByChat(ownerChatId, size).stream().map(views::create).toList(),
				tasks.countPendingByChat(ownerChatId));
		log.info("Listed {} of {} pending tasks", pending.tasks().size(), pending.total());
		return pending;
	}
}
