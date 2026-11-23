package com.kayo.reminder.application.port.out;

import com.kayo.reminder.domain.Task;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

	/** Stores a new task. Fails if a task with the same id already exists; never overwrites. */
	void save(Task task);

	Optional<Task> findById(UUID id);

	/**
	 * PENDING tasks with a deadline at or before {@code deadlineBefore} that still have a reminder stage left to
	 * send for their urgency, earliest deadline first. Past-deadline tasks are included.
	 */
	List<Task> findReminderCandidates(Instant deadlineBefore);

	/**
	 * Records that reminder {@code stage} was sent at {@code at}. Applies only if the task is still PENDING, its
	 * stored stage is lower than {@code stage} and its deadline is still {@code expectedDeadline} (the one the
	 * decision to send was made on, so a snooze in between is not overwritten); returns whether it was applied.
	 */
	boolean markReminded(UUID id, int stage, Instant at, Instant expectedDeadline);

	/** Sets the task to DONE if it is still PENDING; returns whether it was applied. */
	boolean markDone(UUID id, Instant at);

	/**
	 * Moves the deadline to {@code newDeadline} and resets the reminder stage to 0. Applies only if the task is
	 * PENDING, its deadline is still {@code expectedDeadline} and it has had a reminder in its current cycle
	 * (stage above 0), so a repeated press does not snooze twice; returns whether it was applied.
	 */
	boolean snooze(UUID id, Instant expectedDeadline, Instant newDeadline, Instant at);
}
