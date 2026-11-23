package com.kayo.reminder.application.dto.response;

import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
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
}
