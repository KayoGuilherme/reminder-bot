package com.kayo.reminder.domain.exceptions;

import com.kayo.reminder.domain.models.enums.TaskRule;

public class TaskValidationException extends RuntimeException {

	private final TaskRule rule;

	public TaskValidationException(TaskRule rule) {
		super("Invalid task: " + rule);
		this.rule = rule;
	}

	public TaskRule rule() {
		return rule;
	}
}
