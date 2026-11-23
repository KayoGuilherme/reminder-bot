package com.kayo.reminder.application.port.in;

/** The /tasks command: the chat's pending tasks, earliest deadline first, each with Done and Cancel buttons. */
public interface ListTasksUseCase {

	/** At most this many tasks are shown per call; a final message says how many more are pending. */
	int LIMIT = 20;

	void list(long chatId);
}
