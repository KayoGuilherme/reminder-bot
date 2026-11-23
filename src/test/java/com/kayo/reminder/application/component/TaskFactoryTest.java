package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.exceptions.TaskValidationException;
import com.kayo.reminder.domain.models.enums.TaskRule;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskFactoryTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final Instant LATER = NOW.plusSeconds(3600);

	private static TaskResponse create(String title, Urgency urgency, Instant start, Instant deadline) {
		return new TaskFactory(Clock.fixed(NOW, ZoneOffset.UTC)).create(new CreateTaskRequest(UUID.randomUUID(), 7L, title, urgency, start, deadline));
	}

	static Stream<Arguments> invalid() {
		return Stream.of(
				Arguments.of("empty title", "", Urgency.LOW, null, LATER, TaskRule.TITLE_REQUIRED),
				Arguments.of("whitespace title", "  \t ", Urgency.LOW, null, LATER, TaskRule.TITLE_REQUIRED),
				Arguments.of("null title", null, Urgency.LOW, null, LATER, TaskRule.TITLE_REQUIRED),
				Arguments.of("501-char title", "x".repeat(501), Urgency.LOW, null, LATER, TaskRule.TITLE_TOO_LONG),
				Arguments.of("missing urgency", "a", null, null, LATER, TaskRule.URGENCY_REQUIRED),
				Arguments.of("missing deadline", "a", Urgency.LOW, null, null, TaskRule.DEADLINE_REQUIRED),
				Arguments.of("deadline equals now", "a", Urgency.LOW, null, NOW, TaskRule.DEADLINE_NOT_IN_FUTURE),
				Arguments.of("past deadline", "a", Urgency.LOW, null, NOW.minusSeconds(3600),
						TaskRule.DEADLINE_NOT_IN_FUTURE),
				Arguments.of("start after deadline", "a", Urgency.LOW, LATER.plusSeconds(1), LATER,
						TaskRule.START_AFTER_DEADLINE));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalid")
	void rejectsInvalidTasks(String name, String title, Urgency urgency, Instant start, Instant deadline, TaskRule rule) {
		assertThatThrownBy(() -> create(title, urgency, start, deadline))
				.isInstanceOfSatisfying(TaskValidationException.class, ex -> assertThat(ex.rule()).isEqualTo(rule));
	}

	@Test
	void newTaskIsPendingAndTrimmed() {
		TaskResponse task = create("  send report  ", Urgency.HIGH, null, NOW.plusSeconds(1));
		assertThat(task.title()).isEqualTo("send report");
		assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(task.startAt()).isNull();
		assertThat(task.lastReminderStage()).isZero();
		assertThat(task.createdAt()).isEqualTo(NOW);
		assertThat(task.chatId()).isEqualTo(7L);
	}

	@Test
	void titleOfExactlyMaxLengthIsAccepted() {
		assertThat(create("x".repeat(500), Urgency.LOW, null, LATER).title()).hasSize(500);
	}

	@Test
	void startEqualToDeadlineIsAccepted() {
		assertThat(create("a", Urgency.LOW, LATER, LATER).startAt()).isEqualTo(LATER);
	}

	@Test
	void errorNeverContainsTheTitle() {
		assertThatThrownBy(() -> create("secret ".repeat(100), Urgency.LOW, null, LATER))
				.hasMessageNotContaining("secret");
		assertThatThrownBy(() -> create("secret", Urgency.LOW, null, NOW.minusSeconds(1)))
				.hasMessageNotContaining("secret");
	}

	@Test
	void aStoredTaskMayHaveADeadlineInThePast() {
		Instant past = NOW.minusSeconds(86400);
		TaskResponse task = new TaskResponse(UUID.randomUUID(), 7L, "old", Urgency.LOW, TaskStatus.DONE, null, past, (short) 2,
				past, past, past);
		assertThat(task.deadlineAt()).isEqualTo(past);
		assertThat(task.status()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void dtoImportsNoFrameworkTypes() throws Exception {
		try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/kayo/reminder/application/dto"))) {
			for (var file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				assertThat(java.nio.file.Files.readString(file))
						.doesNotContain("import org.springframework", "import jakarta.persistence", "import org.telegram");
			}
		}
	}
}
