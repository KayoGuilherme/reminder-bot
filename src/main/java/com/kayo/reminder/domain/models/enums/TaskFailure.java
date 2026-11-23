package com.kayo.reminder.domain.models.enums;

public enum TaskFailure {
	INVALID_DATETIME("The date and time must be ISO-8601, like 2026-11-25T18:00:00 or 2026-11-25T18:00:00-03:00."),
	INVALID_LIMIT("The limit must be between 1 and 50."),
	TASK_NOT_FOUND("The task was not found."),
	TASK_NOT_PENDING("The task is already finished."),
	NOT_SNOOZABLE("The task has not been reminded yet, so there is nothing to snooze.");

	private final String sentence;

	TaskFailure(String sentence) {
		this.sentence = sentence;
	}

	public String sentence() {
		return sentence;
	}
}
