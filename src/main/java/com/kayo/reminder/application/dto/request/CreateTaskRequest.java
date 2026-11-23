package com.kayo.reminder.application.dto.request;

import com.kayo.reminder.application.component.StartNotAfterDeadline;
import com.kayo.reminder.domain.models.enums.Urgency;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@StartNotAfterDeadline(message = "START_AFTER_DEADLINE")
public record CreateTaskRequest(
		UUID id,
		long chatId,
		@NotBlank(message = "TITLE_REQUIRED") @Size(max = CreateTaskRequest.MAX_TITLE_LENGTH, message = "TITLE_TOO_LONG") String title,
		@NotNull(message = "URGENCY_REQUIRED") Urgency urgency,
		Instant startAt,
		@NotNull(message = "DEADLINE_REQUIRED") @Future(message = "DEADLINE_NOT_IN_FUTURE") Instant deadlineAt) {

	public static final int MAX_TITLE_LENGTH = 500;
}
