package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;

public class StartNotAfterDeadlineValidator implements ConstraintValidator<StartNotAfterDeadline, CreateTaskRequest> {

	@Override
	public boolean isValid(CreateTaskRequest task, ConstraintValidatorContext context) {
		return Optional.ofNullable(task.startAt()).map(start -> !start.isAfter(task.deadlineAt())).orElse(true);
	}
}
