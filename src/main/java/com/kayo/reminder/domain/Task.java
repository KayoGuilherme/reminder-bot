package com.kayo.reminder.domain;

import com.kayo.reminder.domain.TaskValidationException.Rule;
import java.time.Instant;
import java.util.UUID;

/**
 * A task to be done by a deadline. Build new ones with {@link #create}, which enforces every rule; {@link #restore}
 * rebuilds one from storage without the time rules, because a stored deadline is legitimately in the past by then.
 */
public record Task(
		UUID id,
		long chatId,
		String title,
		Urgency urgency,
		TaskStatus status,
		Instant startAt,
		Instant deadlineAt,
		short lastReminderStage,
		Instant lastRemindedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static final int MAX_TITLE_LENGTH = 500;

	public static Task create(UUID id, long chatId, String title, Urgency urgency, Instant startAt,
			Instant deadlineAt, Instant now) {
		String trimmed = title == null ? "" : title.strip();
		if (trimmed.isEmpty()) {
			throw new TaskValidationException(Rule.TITLE_REQUIRED);
		}
		if (trimmed.length() > MAX_TITLE_LENGTH) {
			throw new TaskValidationException(Rule.TITLE_TOO_LONG);
		}
		if (urgency == null) {
			throw new TaskValidationException(Rule.URGENCY_REQUIRED);
		}
		if (deadlineAt == null) {
			throw new TaskValidationException(Rule.DEADLINE_REQUIRED);
		}
		if (!deadlineAt.isAfter(now)) {
			throw new TaskValidationException(Rule.DEADLINE_NOT_IN_FUTURE);
		}
		if (startAt != null && startAt.isAfter(deadlineAt)) {
			throw new TaskValidationException(Rule.START_AFTER_DEADLINE);
		}
		return new Task(id, chatId, trimmed, urgency, TaskStatus.PENDING, startAt, deadlineAt, (short) 0, null,
				now, now);
	}

	public static Task restore(UUID id, long chatId, String title, Urgency urgency, TaskStatus status,
			Instant startAt, Instant deadlineAt, short lastReminderStage, Instant lastRemindedAt, Instant createdAt,
			Instant updatedAt) {
		return new Task(id, chatId, title, urgency, status, startAt, deadlineAt, lastReminderStage, lastRemindedAt,
				createdAt, updatedAt);
	}
}
