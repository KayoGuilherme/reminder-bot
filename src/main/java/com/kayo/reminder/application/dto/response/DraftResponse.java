package com.kayo.reminder.application.dto.response;

import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.util.UUID;

public record DraftResponse(
		UUID id,
		long chatId,
		String title,
		Urgency urgency,
		boolean urgencyAssumed,
		Instant startAt,
		Instant deadlineAt,
		Instant expiresAt,
		String language) {
}
