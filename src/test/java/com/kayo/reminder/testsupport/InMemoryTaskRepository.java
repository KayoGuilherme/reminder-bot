package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.ReminderPolicy;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A map-backed repository whose reminder queries follow the contract of the real one. */
public class InMemoryTaskRepository implements TaskRepository {

	private static final ReminderPolicy POLICY = ReminderPolicy.defaults();

	public final Map<UUID, Task> tasks = new LinkedHashMap<>();
	/** Ids passed to {@link #markReminded} that were applied, in order. */
	public final List<UUID> recorded = new ArrayList<>();

	@Override
	public void save(Task task) {
		if (tasks.putIfAbsent(task.id(), task) != null) {
			throw new IllegalStateException("duplicate id");
		}
	}

	@Override
	public Optional<Task> findById(UUID id) {
		return Optional.ofNullable(tasks.get(id));
	}

	@Override
	public List<Task> findReminderCandidates(Instant deadlineBefore) {
		return tasks.values().stream()
				.filter(t -> t.status() == TaskStatus.PENDING && !t.deadlineAt().isAfter(deadlineBefore)
						&& t.lastReminderStage() < POLICY.finalStage(t.urgency()))
				.sorted(Comparator.comparing(Task::deadlineAt)).toList();
	}

	@Override
	public boolean markReminded(UUID id, int stage, Instant at, Instant expectedDeadline) {
		Task t = tasks.get(id);
		if (t == null || t.status() != TaskStatus.PENDING || t.lastReminderStage() >= stage
				|| !t.deadlineAt().equals(expectedDeadline)) {
			return false;
		}
		tasks.put(id, Task.restore(t.id(), t.chatId(), t.title(), t.urgency(), t.status(), t.startAt(),
				t.deadlineAt(), (short) stage, at, t.createdAt(), at));
		recorded.add(id);
		return true;
	}

	@Override
	public boolean markDone(UUID id, Instant at) {
		Task t = tasks.get(id);
		if (t == null || t.status() != TaskStatus.PENDING) {
			return false;
		}
		tasks.put(id, Task.restore(t.id(), t.chatId(), t.title(), t.urgency(), TaskStatus.DONE, t.startAt(),
				t.deadlineAt(), t.lastReminderStage(), t.lastRemindedAt(), t.createdAt(), at));
		return true;
	}

	@Override
	public boolean snooze(UUID id, Instant expectedDeadline, Instant newDeadline, Instant at) {
		Task t = tasks.get(id);
		if (t == null || t.status() != TaskStatus.PENDING || !t.deadlineAt().equals(expectedDeadline)
				|| t.lastReminderStage() <= 0) {
			return false;
		}
		tasks.put(id, Task.restore(t.id(), t.chatId(), t.title(), t.urgency(), t.status(), t.startAt(),
				newDeadline, (short) 0, t.lastRemindedAt(), t.createdAt(), at));
		return true;
	}

	@Override
	public List<Task> findPendingByChat(long chatId, int limit) {
		return tasks.values().stream()
				.filter(t -> t.status() == TaskStatus.PENDING && t.chatId() == chatId)
				.sorted(Comparator.comparing(Task::deadlineAt).thenComparing(Task::id))
				.limit(limit).toList();
	}

	@Override
	public long countPendingByChat(long chatId) {
		return tasks.values().stream().filter(t -> t.status() == TaskStatus.PENDING && t.chatId() == chatId).count();
	}

	@Override
	public boolean markCanceled(UUID id, Instant at) {
		Task t = tasks.get(id);
		if (t == null || t.status() != TaskStatus.PENDING) {
			return false;
		}
		tasks.put(id, Task.restore(t.id(), t.chatId(), t.title(), t.urgency(), TaskStatus.CANCELED, t.startAt(),
				t.deadlineAt(), t.lastReminderStage(), t.lastRemindedAt(), t.createdAt(), at));
		return true;
	}

	@Override
	public int deleteFinishedBefore(Instant cutoff) {
		int before = tasks.size();
		tasks.values().removeIf(t -> t.status() != TaskStatus.PENDING && t.updatedAt().isBefore(cutoff));
		return before - tasks.size();
	}

	public Task add(long chatId, String title, Urgency urgency, TaskStatus status,
			Instant deadline) {
		Task task = Task.restore(UUID.randomUUID(), chatId, title, urgency, status, null, deadline, (short) 0, null,
				deadline.minusSeconds(3600), deadline.minusSeconds(3600));
		tasks.put(task.id(), task);
		return task;
	}
}
