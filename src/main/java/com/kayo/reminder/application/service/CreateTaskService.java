package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.DateTimeReader;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.component.TaskViewFactory;
import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.application.dto.request.NewTaskRequest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CreateTaskService {

	private static final Logger log = LoggerFactory.getLogger(CreateTaskService.class);

	private final TaskService tasks;
	private final TaskFactory factory;
	private final DateTimeReader reader;
	private final TaskViewFactory views;
	private final long ownerChatId;
	private final String defaultLanguage;

	public CreateTaskService(TaskService tasks, TaskFactory factory, DateTimeReader reader, TaskViewFactory views,
			AppProperties properties) {
		this.tasks = tasks;
		this.factory = factory;
		this.reader = reader;
		this.views = views;
		this.ownerChatId = properties.telegram().allowedUserId();
		this.defaultLanguage = properties.language();
	}

	public TaskViewResponse create(NewTaskRequest request) {
		TaskResponse task = factory.create(new CreateTaskRequest(UUID.randomUUID(), ownerChatId, request.title(),
				Optional.ofNullable(request.urgency()).orElse(Urgency.MEDIUM), reader.read(request.startAt()).orElse(null),
				reader.read(request.deadline()).orElse(null),
				Optional.ofNullable(request.language()).orElse(defaultLanguage)));
		tasks.save(task);
		log.info("Task {} created", task.id());
		return views.create(task);
	}
}
