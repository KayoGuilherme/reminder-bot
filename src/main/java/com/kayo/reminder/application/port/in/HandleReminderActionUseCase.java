package com.kayo.reminder.application.port.in;

import java.util.UUID;

/** The Done, Snooze and Cancel buttons on a task message (a reminder or an entry of the /tasks list). */
public interface HandleReminderActionUseCase {

	String DONE_PREFIX = "task:done:";
	String SNOOZE_PREFIX = "task:snooze:";
	/** Distinct from the draft's {@code task:no:}: 12 characters plus a 36-character id fit the 64-byte limit. */
	String CANCEL_PREFIX = "task:cancel:";

	/** Marks the task done and replaces the reminder message {@code messageId}, when known, with a confirmation. */
	void done(long chatId, Integer messageId, UUID taskId);

	/** Moves the deadline 10 minutes past the later of now and the deadline and restarts the reminder cycle. */
	void snooze(long chatId, Integer messageId, UUID taskId);

	/** Sets the task to CANCELED and replaces message {@code messageId}, when known, with a confirmation. */
	void cancel(long chatId, Integer messageId, UUID taskId);
}
