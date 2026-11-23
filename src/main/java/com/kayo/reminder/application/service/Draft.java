package com.kayo.reminder.application.service;

import com.kayo.reminder.domain.Urgency;
import java.time.Instant;
import java.util.UUID;

/**
 * A task waiting for the owner's Confirm. Its id becomes the task's primary key, so a second save of the same draft
 * can never create a second row.
 *
 * @param urgencyAssumed the owner gave no urgency and {@code urgency} is the default
 */
record Draft(UUID id, long chatId, String title, Urgency urgency, boolean urgencyAssumed, Instant startAt,
		Instant deadlineAt, Instant expiresAt) {
}
