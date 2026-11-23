package com.kayo.reminder.application.dto.response;

import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskViewResponse(
		UUID id,
		String title,
		Urgency urgency,
		TaskStatus status,
		String language,
		Instant startAt,
		Instant deadlineAt,
		OffsetDateTime deadlineLocal,
		boolean overdue) {
}
