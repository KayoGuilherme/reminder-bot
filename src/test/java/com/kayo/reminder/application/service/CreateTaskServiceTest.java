package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.component.DateTimeReader;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.component.TaskViewFactory;
import com.kayo.reminder.application.dto.request.NewTaskRequest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.dto.response.TaskViewResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.exceptions.TaskValidationException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskRule;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CreateTaskServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

	private final InMemoryTaskService tasks = new InMemoryTaskService();
	private final MutableClock clock = new MutableClock(NOW);
	private final AppProperties properties = TestProperties.of(ZoneId.of("America/Fortaleza"), "pt-BR");
	private final CreateTaskService service = new CreateTaskService(tasks, new TaskFactory(clock),
			new DateTimeReader(properties), new TaskViewFactory(properties, clock), properties);

	private static NewTaskRequest request(String title, String deadline) {
		return new NewTaskRequest(title, deadline, null, null, null);
	}

	@Test
	void aMinimalTaskGetsTheDefaults() {
		TaskViewResponse view = service.create(request("Send the report", "2026-11-25T18:00:00"));

		TaskResponse saved = tasks.tasks.get(view.id());
		assertThat(saved.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(saved.urgency()).isEqualTo(Urgency.MEDIUM);
		assertThat(saved.language()).isEqualTo("pt-BR");
		assertThat(saved.chatId()).isEqualTo(42L);
		assertThat(saved.lastReminderStage()).isZero();
		assertThat(view.title()).isEqualTo("Send the report");
		assertThat(view.overdue()).isFalse();
	}

	@Test
	void aDeadlineWithoutAnOffsetIsReadInTheDisplayTimezone() {
		TaskViewResponse view = service.create(request("a", "2026-11-25T18:00:00"));

		assertThat(view.deadlineAt()).isEqualTo(Instant.parse("2026-11-25T21:00:00Z"));
		assertThat(tasks.tasks.get(view.id()).deadlineAt()).isEqualTo(Instant.parse("2026-11-25T21:00:00Z"));
	}

	@Test
	void everyOptionIsHonored() {
		TaskViewResponse view = service.create(new NewTaskRequest("  Pay rent  ", "2026-11-25T18:00:00-03:00", Urgency.HIGH,
				"2026-11-24T08:00:00Z", "en"));

		TaskResponse saved = tasks.tasks.get(view.id());
		assertThat(saved.title()).isEqualTo("Pay rent");
		assertThat(saved.urgency()).isEqualTo(Urgency.HIGH);
		assertThat(saved.language()).isEqualTo("en");
		assertThat(saved.startAt()).isEqualTo(Instant.parse("2026-11-24T08:00:00Z"));
	}

	@Test
	void aPastDeadlineSavesNothing() {
		assertThatThrownBy(() -> service.create(request("a", "2026-11-22T09:00:00Z")))
				.isInstanceOf(TaskValidationException.class)
				.extracting(error -> ((TaskValidationException) error).rule()).isEqualTo(TaskRule.DEADLINE_NOT_IN_FUTURE);
		assertThat(tasks.tasks).isEmpty();
	}

	@Test
	void aMissingDeadlineSavesNothing() {
		assertThatThrownBy(() -> service.create(request("a", null))).isInstanceOf(TaskValidationException.class)
				.extracting(error -> ((TaskValidationException) error).rule()).isEqualTo(TaskRule.DEADLINE_REQUIRED);
		assertThat(tasks.tasks).isEmpty();
	}

	@Test
	void aBlankTitleSavesNothing() {
		assertThatThrownBy(() -> service.create(request("   ", "2026-11-25T18:00:00")))
				.isInstanceOf(TaskValidationException.class)
				.extracting(error -> ((TaskValidationException) error).rule()).isEqualTo(TaskRule.TITLE_REQUIRED);
		assertThat(tasks.tasks).isEmpty();
	}

	@Test
	void aTitleOf501CharactersSavesNothing() {
		assertThatThrownBy(() -> service.create(request("x".repeat(501), "2026-11-25T18:00:00")))
				.isInstanceOf(TaskValidationException.class)
				.extracting(error -> ((TaskValidationException) error).rule()).isEqualTo(TaskRule.TITLE_TOO_LONG);
		assertThat(tasks.tasks).isEmpty();
	}

	@Test
	void aStartAfterTheDeadlineSavesNothing() {
		assertThatThrownBy(() -> service.create(
				new NewTaskRequest("a", "2026-11-25T18:00:00", null, "2026-11-26T18:00:00", null)))
				.isInstanceOf(TaskValidationException.class)
				.extracting(error -> ((TaskValidationException) error).rule()).isEqualTo(TaskRule.START_AFTER_DEADLINE);
		assertThat(tasks.tasks).isEmpty();
	}

	@Test
	void anUnreadableDeadlineSavesNothing() {
		assertThatThrownBy(() -> service.create(request("a", "tomorrow evening"))).isInstanceOf(TaskActionException.class)
				.extracting(error -> ((TaskActionException) error).failure()).isEqualTo(TaskFailure.INVALID_DATETIME);
		assertThat(tasks.tasks).isEmpty();
	}

	@Test
	void anUnsupportedLanguageSavesNothing() {
		assertThatThrownBy(() -> service.create(new NewTaskRequest("a", "2026-11-25T18:00:00", null, null, "fr")))
				.isInstanceOf(TaskValidationException.class)
				.extracting(error -> ((TaskValidationException) error).rule()).isEqualTo(TaskRule.LANGUAGE_UNSUPPORTED);
		assertThat(tasks.tasks).isEmpty();
	}
}
