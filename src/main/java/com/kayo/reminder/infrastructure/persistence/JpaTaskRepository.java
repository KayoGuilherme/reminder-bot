package com.kayo.reminder.infrastructure.persistence;

import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.Task;
import jakarta.persistence.EntityManager;
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

	JpaTaskRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
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
}
