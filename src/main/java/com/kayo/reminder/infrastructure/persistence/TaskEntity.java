package com.kayo.reminder.infrastructure.persistence;

import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of the {@code tasks} table (migration V1). The schema is owned by Flyway; Hibernate only validates it. */
@Entity
@Table(name = "tasks")
class TaskEntity {

	@Id
	private UUID id;

	@Column(name = "chat_id", nullable = false)
	private long chatId;

	@Column(nullable = false, length = 500)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Urgency urgency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TaskStatus status;

	@Column(name = "start_at")
	private Instant startAt;

	@Column(name = "deadline_at", nullable = false)
	private Instant deadlineAt;

	@Column(name = "last_reminder_stage", nullable = false)
	private short lastReminderStage;

	@Column(name = "last_reminded_at")
	private Instant lastRemindedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected TaskEntity() {
	}

	static TaskEntity from(Task task) {
		TaskEntity entity = new TaskEntity();
		entity.id = task.id();
		entity.chatId = task.chatId();
		entity.title = task.title();
		entity.urgency = task.urgency();
		entity.status = task.status();
		entity.startAt = task.startAt();
		entity.deadlineAt = task.deadlineAt();
		entity.lastReminderStage = task.lastReminderStage();
		entity.lastRemindedAt = task.lastRemindedAt();
		entity.createdAt = task.createdAt();
		entity.updatedAt = task.updatedAt();
		return entity;
	}

	Task toDomain() {
		return Task.restore(id, chatId, title, urgency, status, startAt, deadlineAt, lastReminderStage,
				lastRemindedAt, createdAt, updatedAt);
	}
}
