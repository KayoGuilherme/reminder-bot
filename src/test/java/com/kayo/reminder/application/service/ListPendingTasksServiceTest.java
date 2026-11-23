package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.component.TaskViewFactory;
import com.kayo.reminder.application.dto.response.PendingTasksResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListPendingTasksServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
	private static final long OWNER = 42L;

	private final InMemoryTaskService tasks = new InMemoryTaskService();
	private final ListPendingTasksService service = new ListPendingTasksService(tasks,
			new TaskViewFactory(TestProperties.of(ZoneId.of("UTC"), "en"), new MutableClock(NOW)),
			TestProperties.of(ZoneId.of("UTC"), "en"));

	private void add(String title, TaskStatus status, long chat, long hoursAhead) {
		tasks.add(chat, title, Urgency.LOW, status, NOW.plusSeconds(hoursAhead * 3600));
	}

	private List<String> titles(PendingTasksResponse response) {
		return response.tasks().stream().map(TaskViewResponse::title).toList();
	}

	@Test
	void pendingTasksAreOrderedByDeadline() {
		add("18:00", TaskStatus.PENDING, OWNER, 6);
		add("09:00", TaskStatus.PENDING, OWNER, 1);
		add("12:30", TaskStatus.PENDING, OWNER, 3);

		assertThat(titles(service.list(null))).containsExactly("09:00", "12:30", "18:00");
	}

	@Test
	void anOverdueTaskComesFirst() {
		add("tomorrow", TaskStatus.PENDING, OWNER, 24);
		add("late", TaskStatus.PENDING, OWNER, -1);

		PendingTasksResponse response = service.list(null);

		assertThat(titles(response)).containsExactly("late", "tomorrow");
		assertThat(response.tasks().get(0).overdue()).isTrue();
	}

	@Test
	void finishedTasksAreHidden() {
		add("pending", TaskStatus.PENDING, OWNER, 1);
		add("done", TaskStatus.DONE, OWNER, 1);
		add("canceled", TaskStatus.CANCELED, OWNER, 1);

		PendingTasksResponse response = service.list(null);

		assertThat(titles(response)).containsExactly("pending");
		assertThat(response.total()).isEqualTo(1);
	}

	@Test
	void anotherChatsTasksAreHidden() {
		add("mine", TaskStatus.PENDING, OWNER, 1);
		add("theirs", TaskStatus.PENDING, 99L, 1);

		PendingTasksResponse response = service.list(null);

		assertThat(titles(response)).containsExactly("mine");
		assertThat(response.total()).isEqualTo(1);
	}

	@Test
	void noTasksIsAnEmptyListNotAnError() {
		PendingTasksResponse response = service.list(null);

		assertThat(response.tasks()).isEmpty();
		assertThat(response.total()).isZero();
	}

	@Test
	void theLimitCapsTheListButNotTheTotal() {
		IntStream.range(0, 30).forEach(index -> add("task " + index, TaskStatus.PENDING, OWNER, index + 1));

		PendingTasksResponse response = service.list(5);

		assertThat(response.tasks()).hasSize(5);
		assertThat(response.total()).isEqualTo(30);
	}

	@Test
	void theDefaultLimitIsTwenty() {
		IntStream.range(0, 30).forEach(index -> add("task " + index, TaskStatus.PENDING, OWNER, index + 1));

		assertThat(service.list(null).tasks()).hasSize(20);
	}

	@Test
	void aLimitOutsideOneToFiftyIsRefused() {
		assertThatThrownBy(() -> service.list(0)).isInstanceOf(TaskActionException.class)
				.extracting(error -> ((TaskActionException) error).failure()).isEqualTo(TaskFailure.INVALID_LIMIT);
		assertThatThrownBy(() -> service.list(51)).isInstanceOf(TaskActionException.class)
				.extracting(error -> ((TaskActionException) error).failure()).isEqualTo(TaskFailure.INVALID_LIMIT);
		assertThat(service.list(50)).isNotNull();
		assertThat(service.list(1)).isNotNull();
	}
}
