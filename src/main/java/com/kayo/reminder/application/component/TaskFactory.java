package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.exceptions.TaskValidationException;
import com.kayo.reminder.domain.models.enums.TaskRule;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TaskFactory {

	private final Clock clock;
	private final Validator validator;

	public TaskFactory(Clock clock) {
		this.clock = clock;
		this.validator = Validation.byDefaultProvider().configure().clockProvider(() -> clock).buildValidatorFactory()
				.getValidator();
	}

	public TaskResponse create(CreateTaskRequest request) {
		CreateTaskRequest normalized = new CreateTaskRequest(request.id(), request.chatId(),
				Optional.ofNullable(request.title()).map(String::strip).orElse(""), request.urgency(),
				request.startAt(), request.deadlineAt());
		validator.validate(normalized).stream().map(violation -> TaskRule.valueOf(violation.getMessage()))
				.min(Comparator.naturalOrder()).ifPresent(rule -> {
					throw new TaskValidationException(rule);
				});
		Instant now = clock.instant();
		return new TaskResponse(normalized.id(), normalized.chatId(), normalized.title(), normalized.urgency(),
				TaskStatus.PENDING, normalized.startAt(), normalized.deadlineAt(), (short) 0, null, now, now);
	}
}
