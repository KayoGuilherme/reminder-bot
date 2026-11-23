package com.kayo.reminder.infrastructure.persistence;

import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.ReminderPolicy;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uses {@code persist} on purpose: Spring Data's {@code save} on an entity with an assigned id would {@code merge}
 * and silently overwrite an existing row, but a task id must be stored at most once.
 */
@Repository
class JpaTaskRepository implements TaskRepository {

	private final EntityManager entityManager;
	private final ReminderPolicy reminderPolicy;

	JpaTaskRepository(EntityManager entityManager, ReminderPolicy reminderPolicy) {
		this.entityManager = entityManager;
		this.reminderPolicy = reminderPolicy;
	}

	@Override
	@Transactional
	public void save(Task task) {
		entityManager.persist(TaskEntity.from(task));
		entityManager.flush();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Task> findById(UUID id) {
		return Optional.ofNullable(entityManager.find(TaskEntity.class, id)).map(TaskEntity::toDomain);
	}

	/** The per-urgency final stage comes from the domain, so the stage numbers live in one place. */
	@Override
	@Transactional(readOnly = true)
	public List<Task> findReminderCandidates(Instant deadlineBefore) {
		return entityManager.createQuery("""
				select t from TaskEntity t
				where t.status = :pending and t.deadlineAt <= :cutoff
				  and ((t.urgency = :low and t.lastReminderStage < :lowFinal)
				    or (t.urgency = :medium and t.lastReminderStage < :mediumFinal)
				    or (t.urgency = :high and t.lastReminderStage < :highFinal))
				order by t.deadlineAt, t.id
				""", TaskEntity.class)
				.setParameter("pending", TaskStatus.PENDING)
				.setParameter("cutoff", deadlineBefore)
				.setParameter("low", Urgency.LOW)
				.setParameter("lowFinal", (short) reminderPolicy.finalStage(Urgency.LOW))
				.setParameter("medium", Urgency.MEDIUM)
				.setParameter("mediumFinal", (short) reminderPolicy.finalStage(Urgency.MEDIUM))
				.setParameter("high", Urgency.HIGH)
				.setParameter("highFinal", (short) reminderPolicy.finalStage(Urgency.HIGH))
				.getResultList().stream().map(TaskEntity::toDomain).toList();
	}

	@Override
	@Transactional
	public boolean markReminded(UUID id, int stage, Instant at, Instant expectedDeadline) {
		int updated = entityManager.createQuery("""
				update TaskEntity t
				set t.lastReminderStage = :stage, t.lastRemindedAt = :at, t.updatedAt = :at
				where t.id = :id and t.status = :pending and t.lastReminderStage < :stage
				  and t.deadlineAt = :deadline
				""")
				.setParameter("stage", (short) stage)
				.setParameter("at", at)
				.setParameter("id", id)
				.setParameter("pending", TaskStatus.PENDING)
				.setParameter("deadline", expectedDeadline)
				.executeUpdate();
		return updated == 1;
	}

	@Override
	@Transactional
	public boolean markDone(UUID id, Instant at) {
		int updated = entityManager.createQuery("""
				update TaskEntity t
				set t.status = :done, t.updatedAt = :at
				where t.id = :id and t.status = :pending
				""")
				.setParameter("done", TaskStatus.DONE)
				.setParameter("at", at)
				.setParameter("id", id)
				.setParameter("pending", TaskStatus.PENDING)
				.executeUpdate();
		return updated == 1;
	}

	@Override
	@Transactional
	public boolean snooze(UUID id, Instant expectedDeadline, Instant newDeadline, Instant at) {
		int updated = entityManager.createQuery("""
				update TaskEntity t
				set t.deadlineAt = :newDeadline, t.lastReminderStage = 0, t.updatedAt = :at
				where t.id = :id and t.status = :pending and t.deadlineAt = :expected and t.lastReminderStage > 0
				""")
				.setParameter("newDeadline", newDeadline)
				.setParameter("at", at)
				.setParameter("id", id)
				.setParameter("pending", TaskStatus.PENDING)
				.setParameter("expected", expectedDeadline)
				.executeUpdate();
		return updated == 1;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Task> findPendingByChat(long chatId, int limit) {
		return entityManager.createQuery("""
				select t from TaskEntity t
				where t.status = :pending and t.chatId = :chatId
				order by t.deadlineAt, t.id
				""", TaskEntity.class)
				.setParameter("pending", TaskStatus.PENDING)
				.setParameter("chatId", chatId)
				.setMaxResults(limit)
				.getResultList().stream().map(TaskEntity::toDomain).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public long countPendingByChat(long chatId) {
		return entityManager.createQuery(
				"select count(t) from TaskEntity t where t.status = :pending and t.chatId = :chatId", Long.class)
				.setParameter("pending", TaskStatus.PENDING)
				.setParameter("chatId", chatId)
				.getSingleResult();
	}

	@Override
	@Transactional
	public boolean markCanceled(UUID id, Instant at) {
		int updated = entityManager.createQuery("""
				update TaskEntity t
				set t.status = :canceled, t.updatedAt = :at
				where t.id = :id and t.status = :pending
				""")
				.setParameter("canceled", TaskStatus.CANCELED)
				.setParameter("at", at)
				.setParameter("id", id)
				.setParameter("pending", TaskStatus.PENDING)
				.executeUpdate();
		return updated == 1;
	}

	@Override
	@Transactional
	public int deleteFinishedBefore(Instant cutoff) {
		return entityManager.createQuery("""
				delete from TaskEntity t
				where t.status in (:finished) and t.updatedAt < :cutoff
				""")
				.setParameter("finished", List.of(TaskStatus.DONE, TaskStatus.CANCELED))
				.setParameter("cutoff", cutoff)
				.executeUpdate();
	}
}
