package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.service.TaskService;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OwnedTaskLookup {

	private final TaskService tasks;
	private final long ownerChatId;

	public OwnedTaskLookup(TaskService tasks, AppProperties properties) {
		this.tasks = tasks;
		this.ownerChatId = properties.telegram().allowedUserId();
	}

	public TaskResponse find(String id) {
		return identifier(id).flatMap(tasks::findById).filter(task -> task.chatId() == ownerChatId)
				.orElseThrow(() -> new TaskActionException(TaskFailure.TASK_NOT_FOUND));
	}

	private static Optional<UUID> identifier(String id) {
		try {
			return Optional.of(UUID.fromString(id));
		}
		catch (IllegalArgumentException | NullPointerException notAnId) {
			return Optional.empty();
		}
	}
}
