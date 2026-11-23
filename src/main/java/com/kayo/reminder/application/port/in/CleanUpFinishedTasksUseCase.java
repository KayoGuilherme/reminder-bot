package com.kayo.reminder.application.port.in;

/** The retention job: removes DONE and CANCELED tasks that finished longer ago than {@code app.retention.days}. */
public interface CleanUpFinishedTasksUseCase {

	/** Returns how many tasks were deleted. PENDING tasks are never deleted. */
	int deleteExpired();
}
