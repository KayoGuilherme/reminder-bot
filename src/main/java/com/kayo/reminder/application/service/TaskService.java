package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.TaskMapper;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.domain.repositories.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

	private final TaskRepository repository;
	private final TaskMapper mapper;

	public TaskService(TaskRepository repository, TaskMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Transactional
	public void save(TaskResponse task) {
		repository.saveAndFlush(mapper.toEntity(task));
	}

	@Transactional(readOnly = true)
	public Optional<TaskResponse> findById(UUID id) {
		return repository.findById(id).map(mapper::toModel);
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> findReminderCandidates(Instant deadlineBefore, Map<Urgency, Integer> finalStageByUrgency) {
		return repository.findReminderCandidates(deadlineBefore, stage(finalStageByUrgency, Urgency.LOW),
				stage(finalStageByUrgency, Urgency.MEDIUM), stage(finalStageByUrgency, Urgency.HIGH))
				.stream().map(mapper::toModel).toList();
	}

	@Transactional
	public boolean markReminded(UUID id, int stage, Instant at, Instant expectedDeadline) {
		return repository.markReminded(id, (short) stage, at, expectedDeadline) == 1;
	}

	@Transactional
	public boolean markDone(UUID id, Instant at) {
		return repository.finishPending(id, TaskStatus.DONE, at) == 1;
	}

	@Transactional
	public boolean markCanceled(UUID id, Instant at) {
		return repository.finishPending(id, TaskStatus.CANCELED, at) == 1;
	}

	@Transactional
	public boolean snooze(UUID id, Instant expectedDeadline, Instant newDeadline, Instant at) {
		return repository.snooze(id, expectedDeadline, newDeadline, at) == 1;
	}

	@Transactional(readOnly = true)
	public List<TaskResponse> findPendingByChat(long chatId, int limit) {
		return repository.findByChatIdAndStatusOrderByDeadlineAtAscIdAsc(chatId, TaskStatus.PENDING,
				PageRequest.ofSize(limit)).stream().map(mapper::toModel).toList();
	}

	@Transactional(readOnly = true)
	public long countPendingByChat(long chatId) {
		return repository.countByChatIdAndStatus(chatId, TaskStatus.PENDING);
	}

	@Transactional
	public int deleteFinishedBefore(Instant cutoff) {
		return repository.deleteFinishedBefore(cutoff);
	}

	private static short stage(Map<Urgency, Integer> finalStageByUrgency, Urgency urgency) {
		return finalStageByUrgency.get(urgency).shortValue();
	}
}
