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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SnoozeTaskServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

	private final InMemoryTaskService tasks = new InMemoryTaskService();
	private final MutableClock clock = new MutableClock(NOW);
	private final AppProperties properties = TestProperties.of(ZoneId.of("UTC"), "en");
	private final SnoozeTaskService service = new SnoozeTaskService(tasks, new OwnedTaskLookup(tasks, properties),
			new TaskViewFactory(properties, clock), clock);

	private TaskResponse reminded(Duration untilDeadline, TaskStatus status) {
		TaskResponse task = tasks.add(42L, "a", Urgency.HIGH, status, NOW.plus(untilDeadline));
		tasks.markReminded(task.id(), 1, NOW, task.deadlineAt());
		return tasks.tasks.get(task.id());
	}

	private static TaskFailure failureOf(Throwable error) {
		return ((TaskActionException) error).failure();
	}

	@Test
	void aRemindedTaskDueSoonMovesTenMinutesPastItsDeadline() {
		TaskResponse task = reminded(Duration.ofMinutes(4), TaskStatus.PENDING);

		TaskViewResponse view = service.snooze(task.id().toString());

		assertThat(view.deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(14)));
		TaskResponse saved = tasks.tasks.get(task.id());
		assertThat(saved.lastReminderStage()).isZero();
		assertThat(saved.urgency()).isEqualTo(Urgency.HIGH);
		assertThat(saved.startAt()).isEqualTo(task.startAt());
	}

	@Test
	void anOverdueRemindedTaskMovesTenMinutesPastNow() {
		TaskResponse task = reminded(Duration.ofMinutes(-30), TaskStatus.PENDING);

		TaskViewResponse view = service.snooze(task.id().toString());

		assertThat(view.deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		assertThat(view.overdue()).isFalse();
	}

	@Test
	void aTaskNeverRemindedCannotBeSnoozed() {
		TaskResponse task = tasks.add(42L, "a", Urgency.LOW, TaskStatus.PENDING, NOW.plus(Duration.ofHours(1)));

		assertThatThrownBy(() -> service.snooze(task.id().toString())).isInstanceOf(TaskActionException.class)
				.satisfies(error -> assertThat(failureOf(error)).isEqualTo(TaskFailure.NOT_SNOOZABLE));
		assertThat(tasks.tasks.get(task.id()).deadlineAt()).isEqualTo(task.deadlineAt());
	}

	@Test
	void aFinishedTaskCannotBeSnoozed() {
		TaskResponse task = reminded(Duration.ofMinutes(4), TaskStatus.DONE);

		assertThatThrownBy(() -> service.snooze(task.id().toString())).isInstanceOf(TaskActionException.class)
				.satisfies(error -> assertThat(failureOf(error)).isEqualTo(TaskFailure.TASK_NOT_PENDING));
	}

	@Test
	void anUnknownIdIsNotFound() {
		assertThatThrownBy(() -> service.snooze("abc")).isInstanceOf(TaskActionException.class)
				.satisfies(error -> assertThat(failureOf(error)).isEqualTo(TaskFailure.TASK_NOT_FOUND));
	}
}
