package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.SendResult.FailureReason;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskRepository;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReminderActionServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
	private static final long CHAT = 7L;
	private static final int MESSAGE = 77;

	private final InMemoryTaskRepository repository = new InMemoryTaskRepository();
	private final RecordingSender sender = new RecordingSender();
	private final MutableClock clock = new MutableClock(NOW);

	private ReminderActionService service(String language) {
		return new ReminderActionService(repository, sender, TestMessages.of(language),
				TestProperties.of(ZoneId.of("UTC"), language), clock);
	}

	private ReminderActionService service() {
		return service("en");
	}

	private static String text(String key) {
		return TestMessages.text("en", key);
	}

	/** A reminded task: {@code stage} is what the last reminder was and {@code deadline} when it is due. */
	private Task task(TaskStatus status, Instant deadline, int stage) {
		Task added = repository.add(CHAT, "Send the report", Urgency.HIGH, status, deadline);
		Task stored = Task.restore(added.id(), CHAT, added.title(), Urgency.HIGH, status, null, deadline,
				(short) stage, NOW, added.createdAt(), added.updatedAt());
		repository.tasks.put(stored.id(), stored);
		return stored;
	}

	private Task stored(Task task) {
		return repository.tasks.get(task.id());
	}

	// --- Done ------------------------------------------------------------------------------------------------

	@Test
	void doneMarksThePendingTaskDoneAndEditsTheMessageIntoAConfirmation() {
		Task task = task(TaskStatus.PENDING, NOW, 5);

		service().done(CHAT, MESSAGE, task.id());

		assertThat(stored(task).status()).isEqualTo(TaskStatus.DONE);
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.edits.get(0).chatId()).isEqualTo(CHAT);
		assertThat(sender.edits.get(0).messageId()).isEqualTo(MESSAGE);
		assertThat(sender.edits.get(0).text()).isEqualTo("Done ✅\n\nSend the report");
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void doneTwiceLeavesItDoneAndRepliesPolitely() {
		Task task = task(TaskStatus.PENDING, NOW, 5);
		ReminderActionService service = service();
		service.done(CHAT, MESSAGE, task.id());

		service.done(CHAT, 70, task.id());

		assertThat(stored(task).status()).isEqualTo(TaskStatus.DONE);
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("reminder.action.already-done"));
	}

	@Test
	void doneOnACanceledTaskChangesNothingAndSaysItIsNotActive() {
		Task task = task(TaskStatus.CANCELED, NOW, 1);

		service().done(CHAT, MESSAGE, task.id());

		assertThat(stored(task).status()).isEqualTo(TaskStatus.CANCELED);
		assertThat(sender.edits).isEmpty();
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("reminder.action.not-active"));
	}

	@Test
	void doneOnAnUnknownTaskRepliesThatItIsNotAvailable() {
		service().done(CHAT, MESSAGE, UUID.randomUUID());

		assertThat(sender.edits).isEmpty();
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("reminder.action.not-available"));
	}

	@Test
	void aTaskOfAnotherChatIsAnsweredLikeAMissingOneAndLeftAlone() {
		Task task = task(TaskStatus.PENDING, NOW, 1);

		service().done(999L, MESSAGE, task.id());
		service().snooze(999L, MESSAGE, task.id());

		assertThat(stored(task)).isEqualTo(task);
		assertThat(sender.edits).isEmpty();
		assertThat(sender.texts).hasSize(2).allSatisfy(t -> {
			assertThat(t.chatId()).isEqualTo(999L);
			assertThat(t.text()).isEqualTo(text("reminder.action.not-available"));
		});
	}

	@Test
	void whenTheEditFailsTheConfirmationIsSentAsANewMessageAndDoneIsNotRepeated() {
		Task task = task(TaskStatus.PENDING, NOW, 1);
		sender.result = SendResult.failed(FailureReason.REJECTED);

		service().done(CHAT, MESSAGE, task.id());

		assertThat(stored(task).status()).isEqualTo(TaskStatus.DONE);
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly("Done ✅\n\nSend the report");
	}

	@Test
	void withoutAMessageIdTheConfirmationIsANewMessage() {
		Task task = task(TaskStatus.PENDING, NOW, 1);

		service().done(CHAT, null, task.id());

		assertThat(sender.edits).isEmpty();
		assertThat(sender.texts).hasSize(1);
	}

	// --- Snooze ----------------------------------------------------------------------------------------------

	@Test
	void snoozeBeforeTheDeadlineAddsTenMinutesToTheDeadlineAndResetsTheStage() {
		Instant deadline = NOW.plus(Duration.ofMinutes(4));
		Task task = task(TaskStatus.PENDING, deadline, 1);

		service().snooze(CHAT, MESSAGE, task.id());

		Task after = stored(task);
		assertThat(after.deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(14)));
		assertThat(after.lastReminderStage()).isZero();
		assertThat(after.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(after.urgency()).isEqualTo(Urgency.HIGH);
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.edits.get(0).messageId()).isEqualTo(MESSAGE);
		assertThat(sender.edits.get(0).text()).startsWith("Snoozed for 10 minutes")
				.contains("Send the report").contains("2026-11-22 15:14");
	}

	@Test
	void snoozeAfterTheDeadlineCountsFromNow() {
		Task task = task(TaskStatus.PENDING, NOW.minus(Duration.ofMinutes(20)), 6);

		service().snooze(CHAT, MESSAGE, task.id());

		assertThat(stored(task).deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		assertThat(stored(task).lastReminderStage()).isZero();
	}

	@Test
	void snoozeTwiceDoesNotMoveTheDeadlineASecondTime() {
		Task task = task(TaskStatus.PENDING, NOW.minus(Duration.ofMinutes(20)), 6);
		ReminderActionService service = service();
		service.snooze(CHAT, MESSAGE, task.id());
		Instant afterFirst = stored(task).deadlineAt();

		service.snooze(CHAT, 70, task.id());

		assertThat(stored(task).deadlineAt()).isEqualTo(afterFirst);
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("reminder.action.already-snoozed"));
	}

	@Test
	void snoozeOnFinishedOrUnknownTasksChangesNothingAndRepliesPolitely() {
		Task done = task(TaskStatus.DONE, NOW, 1);
		Task canceled = task(TaskStatus.CANCELED, NOW, 1);
		ReminderActionService service = service();

		service.snooze(CHAT, MESSAGE, done.id());
		service.snooze(CHAT, MESSAGE, canceled.id());
		service.snooze(CHAT, MESSAGE, UUID.randomUUID());

		assertThat(stored(done).deadlineAt()).isEqualTo(NOW);
		assertThat(stored(canceled).deadlineAt()).isEqualTo(NOW);
		assertThat(sender.edits).isEmpty();
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("reminder.action.not-active"),
				text("reminder.action.not-active"), text("reminder.action.not-available"));
	}

	@Test
	void snoozeFallsBackToANewMessageWhenTheEditFails() {
		Task task = task(TaskStatus.PENDING, NOW, 2);
		sender.result = SendResult.failed(FailureReason.TRANSIENT);

		service().snooze(CHAT, MESSAGE, task.id());

		assertThat(stored(task).deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
		assertThat(sender.texts).hasSize(1);
		assertThat(sender.texts.get(0).text()).startsWith("Snoozed");
	}

	@Test
	void textsAreInThePortugueseWhenConfigured() {
		Task task = task(TaskStatus.PENDING, NOW, 1);

		service("pt-BR").snooze(CHAT, MESSAGE, task.id());
		service("pt-BR").done(CHAT, MESSAGE, task.id());

		assertThat(sender.edits.get(0).text()).startsWith("Adiada por 10 minutos").contains("Novo prazo");
		assertThat(sender.edits.get(1).text()).startsWith("Feito");
	}
}
