package com.kayo.reminder.domain;

/**
 * The reminder stage (1-based) that is due for a task, and what kind of reminder it is. {@code repeat} is the
 * number of the overdue reminder (1-based) for {@link Kind#OVERDUE}, how many overdue reminders were sent for
 * {@link Kind#FINAL}, and 0 for {@link Kind#SCHEDULED}.
 */
public record ReminderDecision(int stage, Kind kind, int repeat) {

	public enum Kind {
		/** Due at a fixed offset before the deadline. */
		SCHEDULED,
		/** A HIGH task is past its deadline and still pending. */
		OVERDUE,
		/** The last message of a HIGH task: the bot stops reminding. */
		FINAL
	}

	public static ReminderDecision scheduled(int stage) {
		return new ReminderDecision(stage, Kind.SCHEDULED, 0);
	}

	public static ReminderDecision overdue(int stage, int repeat) {
		return new ReminderDecision(stage, Kind.OVERDUE, repeat);
	}

	public static ReminderDecision finalStop(int stage, int repeats) {
		return new ReminderDecision(stage, Kind.FINAL, repeats);
	}
}
