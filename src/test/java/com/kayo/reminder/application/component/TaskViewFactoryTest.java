package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TaskViewFactoryTest {

	private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

	private final InMemoryTaskService tasks = new InMemoryTaskService();
	private final TaskViewFactory factory = new TaskViewFactory(
			TestProperties.of(ZoneId.of("America/Fortaleza"), "en"), new MutableClock(NOW));

	private TaskResponse task(TaskStatus status, Instant deadline) {
		return tasks.add(42L, "Send the report", Urgency.HIGH, status, deadline);
	}

	@Test
	void theDeadlineIsShownInUtcAndInTheDisplayTimezone() {
		TaskViewResponse view = factory.create(task(TaskStatus.PENDING, Instant.parse("2026-11-22T21:00:00Z")));

		assertThat(view.deadlineAt()).isEqualTo(Instant.parse("2026-11-22T21:00:00Z"));
		assertThat(view.deadlineLocal()).hasToString("2026-11-22T18:00-03:00");
		assertThat(view.deadlineLocal().toInstant()).isEqualTo(view.deadlineAt());
	}

	@Test
	void theViewCarriesTheTaskFields() {
		TaskResponse task = task(TaskStatus.PENDING, NOW.plus(Duration.ofHours(1)));

		TaskViewResponse view = factory.create(task);

		assertThat(view.id()).isEqualTo(task.id());
		assertThat(view.title()).isEqualTo("Send the report");
		assertThat(view.urgency()).isEqualTo(Urgency.HIGH);
		assertThat(view.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(view.language()).isEqualTo("en");
	}

	@Test
	void aPendingTaskPastItsDeadlineIsOverdue() {
		assertThat(factory.create(task(TaskStatus.PENDING, NOW.minus(Duration.ofMinutes(30)))).overdue()).isTrue();
	}

	@Test
	void aPendingTaskDueInTheFutureIsNotOverdue() {
		assertThat(factory.create(task(TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(30)))).overdue()).isFalse();
	}

	@Test
	void aFinishedTaskIsNeverOverdue() {
		assertThat(factory.create(task(TaskStatus.DONE, NOW.minus(Duration.ofMinutes(30)))).overdue()).isFalse();
		assertThat(factory.create(task(TaskStatus.CANCELED, NOW.minus(Duration.ofMinutes(30)))).overdue()).isFalse();
	}
}
