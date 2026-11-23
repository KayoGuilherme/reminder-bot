package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.component.OwnedTaskLookup;
import com.kayo.reminder.application.component.TaskViewFactory;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinishTaskServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

	private final InMemoryTaskService tasks = new InMemoryTaskService();
	private final MutableClock clock = new MutableClock(NOW);
	private final AppProperties properties = TestProperties.of(ZoneId.of("UTC"), "en");
	private final FinishTaskService service = new FinishTaskService(tasks, new OwnedTaskLookup(tasks, properties),
			new TaskViewFactory(properties, clock), clock);

	private TaskResponse pending() {
		return tasks.add(42L, "a", Urgency.HIGH, TaskStatus.PENDING, NOW.plusSeconds(3600));
	}

	private static TaskFailure failureOf(Throwable error) {
		return ((TaskActionException) error).failure();
	}

	@Test
	void completingAPendingTaskMakesItDone() {
		TaskResponse task = pending();

		TaskViewResponse view = service.complete(task.id().toString());

		assertThat(view.status()).isEqualTo(TaskStatus.DONE);
		assertThat(tasks.tasks.get(task.id()).status()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void cancelingAPendingTaskMakesItCanceled() {
		TaskResponse task = pending();

		assertThat(service.cancel(task.id().toString()).status()).isEqualTo(TaskStatus.CANCELED);
		assertThat(tasks.tasks.get(task.id()).status()).isEqualTo(TaskStatus.CANCELED);
	}

	@Test
	void aFinishedTaskIsNoLongerAReminderCandidate() {
		TaskResponse task = pending();
		service.complete(task.id().toString());

		assertThat(tasks.findReminderCandidates(NOW.plusSeconds(7200), java.util.Map.of(Urgency.LOW, 1,
				Urgency.MEDIUM, 2, Urgency.HIGH, 3))).isEmpty();
	}

	@Test
	void repeatingTheSameActionSucceedsAndChangesNothing() {
		TaskResponse task = pending();
		service.complete(task.id().toString());
		Instant firstUpdate = tasks.tasks.get(task.id()).updatedAt();
		clock.advance(java.time.Duration.ofMinutes(5));

		assertThat(service.complete(task.id().toString()).status()).isEqualTo(TaskStatus.DONE);
		assertThat(tasks.tasks.get(task.id()).updatedAt()).isEqualTo(firstUpdate);
	}

	@Test
	void theOppositeActionOnAFinishedTaskFails() {
		TaskResponse task = pending();
		service.complete(task.id().toString());

		assertThatThrownBy(() -> service.cancel(task.id().toString())).isInstanceOf(TaskActionException.class)
				.satisfies(error -> assertThat(failureOf(error)).isEqualTo(TaskFailure.TASK_NOT_PENDING));
		assertThat(tasks.tasks.get(task.id()).status()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void anUnknownIdIsNotFound() {
		assertThatThrownBy(() -> service.complete(UUID.randomUUID().toString())).isInstanceOf(TaskActionException.class)
				.satisfies(error -> assertThat(failureOf(error)).isEqualTo(TaskFailure.TASK_NOT_FOUND));
	}

	@Test
	void anotherChatsTaskIsNotFoundAndStaysUntouched() {
		TaskResponse foreign = tasks.add(99L, "theirs", Urgency.LOW, TaskStatus.PENDING, NOW.plusSeconds(3600));

		assertThatThrownBy(() -> service.cancel(foreign.id().toString())).isInstanceOf(TaskActionException.class)
				.satisfies(error -> assertThat(failureOf(error)).isEqualTo(TaskFailure.TASK_NOT_FOUND));
		assertThat(tasks.tasks.get(foreign.id()).status()).isEqualTo(TaskStatus.PENDING);
	}
}
