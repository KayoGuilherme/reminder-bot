package com.kayo.reminder.application.port.in;

/** Sends the reminders that are due for pending tasks. Meant to be run repeatedly. */
public interface SendDueRemindersUseCase {

	/** Handles every task that is due now and returns how many reminders were sent. Never throws for one task's failure. */
	int sendDueReminders();
}
