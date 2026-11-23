package com.kayo.reminder.application.port.in;

import java.util.UUID;

/** The Done and Snooze buttons on a reminder message. */
public interface HandleReminderActionUseCase {

	String DONE_PREFIX = "task:done:";
	String SNOOZE_PREFIX = "task:snooze:";

	/** Marks the task done and replaces the reminder message {@code messageId}, when known, with a confirmation. */
	void done(long chatId, Integer messageId, UUID taskId);

	/** Moves the deadline 10 minutes past the later of now and the deadline and restarts the reminder cycle. */
	void snooze(long chatId, Integer messageId, UUID taskId);
}
