package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnedTaskLookupTest {

	private static final Instant DEADLINE = Instant.parse("2026-11-25T21:00:00Z");

	private final InMemoryTaskService tasks = new InMemoryTaskService();
	private final OwnedTaskLookup lookup = new OwnedTaskLookup(tasks, TestProperties.of(ZoneId.of("UTC"), "en"));

	@Test
	void theOwnersTaskIsFound() {
		TaskResponse own = tasks.add(42L, "mine", Urgency.LOW, TaskStatus.PENDING, DEADLINE);

		assertThat(lookup.find(own.id().toString())).isEqualTo(own);
	}

	@Test
	void anUnknownIdIsNotFound() {
		assertThatThrownBy(() -> lookup.find(UUID.randomUUID().toString())).isInstanceOf(TaskActionException.class)
				.hasMessage(TaskFailure.TASK_NOT_FOUND.sentence());
	}

	@Test
	void anotherChatsTaskLooksExactlyLikeAnUnknownOne() {
		TaskResponse foreign = tasks.add(99L, "theirs", Urgency.LOW, TaskStatus.PENDING, DEADLINE);

		assertThatThrownBy(() -> lookup.find(foreign.id().toString())).isInstanceOf(TaskActionException.class)
				.hasMessage(TaskFailure.TASK_NOT_FOUND.sentence());
	}

	@Test
	void textThatIsNotAnIdIsNotFound() {
		assertThatThrownBy(() -> lookup.find("abc")).isInstanceOf(TaskActionException.class)
				.extracting(error -> ((TaskActionException) error).failure()).isEqualTo(TaskFailure.TASK_NOT_FOUND);
		assertThatThrownBy(() -> lookup.find(null)).isInstanceOf(TaskActionException.class);
	}
}
