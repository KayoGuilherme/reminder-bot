package com.kayo.reminder.domain.exceptions;

import com.kayo.reminder.domain.models.enums.TaskFailure;

public class TaskActionException extends RuntimeException {

	private final TaskFailure failure;

	public TaskActionException(TaskFailure failure) {
		super(failure.sentence());
		this.failure = failure;
	}

	public TaskFailure failure() {
		return failure;
	}
}
