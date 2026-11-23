package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.TaskEntity;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

	public TaskEntity toEntity(TaskResponse task) {
		return new TaskEntity().id(task.id()).chatId(task.chatId()).title(task.title()).urgency(task.urgency())
				.status(task.status()).startAt(task.startAt()).deadlineAt(task.deadlineAt())
				.lastReminderStage(task.lastReminderStage()).lastRemindedAt(task.lastRemindedAt())
				.createdAt(task.createdAt()).updatedAt(task.updatedAt()).language(task.language()).fresh(true);
	}

	public TaskResponse toModel(TaskEntity entity) {
		return new TaskResponse(entity.id(), entity.chatId(), entity.title(), entity.urgency(), entity.status(),
				entity.startAt(), entity.deadlineAt(), entity.lastReminderStage(), entity.lastRemindedAt(),
				entity.createdAt(), entity.updatedAt(), entity.language());
	}
}
