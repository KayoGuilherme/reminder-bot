package com.kayo.reminder.application.port.in;

import java.util.UUID;

/** Turns a voice note or text into a task, with a confirmation step in between. */
public interface CreateTaskFromVoiceUseCase {

	String CONFIRM_PREFIX = "task:ok:";
	String CANCEL_PREFIX = "task:no:";

	/** Handles a VOICE or TEXT command: validates, transcribes, parses and replies with a draft to confirm. */
	void submit(IncomingCommand command);

	/** Saves the draft as a pending task. {@code messageId} is the confirmation message, when known. */
	void confirm(long chatId, Integer messageId, UUID draftId);

	/** Discards the draft. */
	void cancel(long chatId, Integer messageId, UUID draftId);
}
