package com.kayo.reminder.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Decides which reminder stage a task is owed at a given instant. Scheduled stages are numbered from 1 and each is
 * due from a fixed offset before the deadline onward; when several unsent scheduled stages are due only the highest
 * is returned, so a catch-up after downtime or a late-created task yields one reminder, never a burst.
 *
 * <p>HIGH tasks continue after their last scheduled stage: overdue reminder n is stage (scheduled stages + n), due
 * once the task is past its deadline and the repeat interval has elapsed since the previous reminder was sent, for
 * n up to the cap; one final stop stage follows the cap and nothing after it. The last sent stage doubles as the
 * repeat counter, so no extra state is stored.
 */
public final class ReminderPolicy {

	/** The earliest any stage becomes due: candidates are tasks whose deadline is within this span. */
	public static final Duration LOOKAHEAD = Duration.ofMinutes(10);

	/** How far a snooze moves the deadline past the later of the current deadline and now. */
	public static final Duration SNOOZE = Duration.ofMinutes(10);

	public static final Duration DEFAULT_REPEAT_INTERVAL = Duration.ofMinutes(2);
	public static final int DEFAULT_MAX_REPEATS = 10;

	private static final List<Duration> LOW = List.of(LOOKAHEAD);
	private static final List<Duration> MEDIUM = List.of(LOOKAHEAD, Duration.ZERO);
	private static final List<Duration> HIGH = List.of(LOOKAHEAD, Duration.ofMinutes(5), Duration.ZERO);

	private final Duration repeatInterval;
	private final int maxRepeats;

	public ReminderPolicy(Duration repeatInterval, int maxRepeats) {
		if (repeatInterval == null || repeatInterval.isZero() || repeatInterval.isNegative()) {
			throw new IllegalArgumentException("The repeat interval must be positive");
		}
		if (maxRepeats < 1) {
			throw new IllegalArgumentException("The maximum number of repeats must be at least 1");
		}
		this.repeatInterval = repeatInterval;
		this.maxRepeats = maxRepeats;
	}

	public static ReminderPolicy defaults() {
		return new ReminderPolicy(DEFAULT_REPEAT_INTERVAL, DEFAULT_MAX_REPEATS);
	}

	/** The last stage of {@code urgency}; once it is sent the task has no more reminders. */
	public int finalStage(Urgency urgency) {
		int scheduled = offsets(urgency).size();
		return urgency == Urgency.HIGH ? scheduled + maxRepeats + 1 : scheduled;
	}

	public Optional<ReminderDecision> decide(Task task, Instant now) {
		if (task.status() != TaskStatus.PENDING) {
			return Optional.empty();
		}
		List<Duration> offsets = offsets(task.urgency());
		int lastStage = task.lastReminderStage();
		for (int stage = offsets.size(); stage > lastStage; stage--) {
			Instant due = task.deadlineAt().minus(offsets.get(stage - 1));
			if (!now.isBefore(due)) {
				return Optional.of(ReminderDecision.scheduled(stage));
			}
		}
		if (task.urgency() != Urgency.HIGH || lastStage < offsets.size() || lastStage >= finalStage(Urgency.HIGH)) {
			return Optional.empty();
		}
		return nextOverdue(task, now, offsets.size());
	}

	/** The reminder after the scheduled stages: only past the deadline and once the previous one is old enough. */
	private Optional<ReminderDecision> nextOverdue(Task task, Instant now, int scheduledStages) {
		if (now.isBefore(task.deadlineAt())) {
			return Optional.empty();
		}
		Instant lastRemindedAt = task.lastRemindedAt();
		if (lastRemindedAt != null && now.isBefore(lastRemindedAt.plus(repeatInterval))) {
			return Optional.empty();
		}
		int stage = task.lastReminderStage() + 1;
		if (stage == finalStage(Urgency.HIGH)) {
			return Optional.of(ReminderDecision.finalStop(stage, maxRepeats));
		}
		return Optional.of(ReminderDecision.overdue(stage, stage - scheduledStages));
	}

	private static List<Duration> offsets(Urgency urgency) {
		return switch (urgency) {
			case LOW -> LOW;
			case MEDIUM -> MEDIUM;
			case HIGH -> HIGH;
		};
	}
}
