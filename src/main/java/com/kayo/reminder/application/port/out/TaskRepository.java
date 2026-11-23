package com.kayo.reminder.application.port.out;

import com.kayo.reminder.domain.Task;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

	/** Stores a new task. Fails if a task with the same id already exists; never overwrites. */
	void save(Task task);

	Optional<Task> findById(UUID id);
}
