package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.service.TaskService;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class InMemoryTaskService extends TaskService {

	public final Map<UUID, TaskResponse> tasks = new LinkedHashMap<>();
	public final List<UUID> recorded = new ArrayList<>();

	public InMemoryTaskService() {
		super(null, null);
	}

	@Override
	public void save(TaskResponse task) {
		Optional.ofNullable(tasks.putIfAbsent(task.id(), task)).ifPresent(existing -> {
			throw new IllegalStateException("duplicate id");
		});
	}

	@Override
	public Optional<TaskResponse> findById(UUID id) {
		return Optional.ofNullable(tasks.get(id));
	}

	@Override
	public List<TaskResponse> findReminderCandidates(Instant deadlineBefore, Map<Urgency, Integer> finalStageByUrgency) {
		return tasks.values().stream()
				.filter(t -> t.status() == TaskStatus.PENDING && !t.deadlineAt().isAfter(deadlineBefore)
						&& t.lastReminderStage() < finalStageByUrgency.get(t.urgency()))
				.sorted(Comparator.comparing(TaskResponse::deadlineAt)).toList();
	}

	@Override
	public boolean markReminded(UUID id, int stage, Instant at, Instant expectedDeadline) {
		boolean applied = update(id,
				task -> task.status() == TaskStatus.PENDING && task.lastReminderStage() < stage
						&& task.deadlineAt().equals(expectedDeadline),
				task -> new TaskResponse(task.id(), task.chatId(), task.title(), task.urgency(), task.status(),
						task.startAt(), task.deadlineAt(), (short) stage, at, task.createdAt(), at, task.language()));
		Optional.of(applied).filter(Boolean::booleanValue).ifPresent(done -> recorded.add(id));
		return applied;
	}

	@Override
	public boolean markDone(UUID id, Instant at) {
		return finish(id, TaskStatus.DONE, at);
	}

	@Override
	public boolean markCanceled(UUID id, Instant at) {
		return finish(id, TaskStatus.CANCELED, at);
	}

	@Override
	public boolean snooze(UUID id, Instant expectedDeadline, Instant newDeadline, Instant at) {
		return update(id,
				task -> task.status() == TaskStatus.PENDING && task.deadlineAt().equals(expectedDeadline)
						&& task.lastReminderStage() > 0,
				task -> new TaskResponse(task.id(), task.chatId(), task.title(), task.urgency(), task.status(),
						task.startAt(), newDeadline, (short) 0, task.lastRemindedAt(), task.createdAt(), at, task.language()));
	}

	@Override
	public List<TaskResponse> findPendingByChat(long chatId, int limit) {
		return tasks.values().stream()
				.filter(t -> t.status() == TaskStatus.PENDING && t.chatId() == chatId)
				.sorted(Comparator.comparing(TaskResponse::deadlineAt).thenComparing(TaskResponse::id))
				.limit(limit).toList();
	}

	@Override
	public long countPendingByChat(long chatId) {
		return tasks.values().stream().filter(t -> t.status() == TaskStatus.PENDING && t.chatId() == chatId).count();
	}

	@Override
	public int deleteFinishedBefore(Instant cutoff) {
		int before = tasks.size();
		tasks.values().removeIf(t -> t.status() != TaskStatus.PENDING && t.updatedAt().isBefore(cutoff));
		return before - tasks.size();
	}

	private boolean finish(UUID id, TaskStatus status, Instant at) {
		return update(id, task -> task.status() == TaskStatus.PENDING,
				task -> new TaskResponse(task.id(), task.chatId(), task.title(), task.urgency(), status, task.startAt(),
						task.deadlineAt(), task.lastReminderStage(), task.lastRemindedAt(), task.createdAt(), at, task.language()));
	}

	private boolean update(UUID id, Predicate<TaskResponse> applicable, UnaryOperator<TaskResponse> change) {
		Optional<TaskResponse> current = Optional.ofNullable(tasks.get(id)).filter(applicable);
		current.ifPresent(task -> tasks.put(id, change.apply(task)));
		return current.isPresent();
	}

	public TaskResponse add(long chatId, String title, Urgency urgency, TaskStatus status,
			Instant deadline) {
		TaskResponse task = new TaskResponse(UUID.randomUUID(), chatId, title, urgency, status, null, deadline, (short) 0, null,
				deadline.minusSeconds(3600), deadline.minusSeconds(3600), "en");
		tasks.put(task.id(), task);
		return task;
	}
}
