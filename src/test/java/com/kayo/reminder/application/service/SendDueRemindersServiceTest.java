package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.SendResult.FailureReason;
import com.kayo.reminder.domain.ReminderPolicy;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskRepository;
import com.kayo.reminder.testsupport.LogCapture;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendDueRemindersServiceTest {

	private static final Instant DEADLINE = Instant.parse("2026-11-22T15:00:00Z");
	private static final Duration TEN = Duration.ofMinutes(10);

	private final InMemoryTaskRepository repository = new InMemoryTaskRepository();
	private final RecordingSender sender = new RecordingSender();
	private final MutableClock clock = new MutableClock(DEADLINE.minus(TEN));
	private SendDueRemindersService service;

	@BeforeEach
	void setUp() {
		service = new SendDueRemindersService(repository, sender, new ReminderMessageFactory(TestMessages.of("en")),
				ReminderPolicy.defaults(), clock);
	}

	private Task task(Urgency urgency) {
		return repository.add(7L, "Send the report", urgency, TaskStatus.PENDING, DEADLINE);
	}

	@Test
	void sendsOneMessageToTheTasksChatAndRecordsTheStage() {
		Task task = task(Urgency.MEDIUM);

		assertThat(service.sendDueReminders()).isEqualTo(1);

		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.texts).isEmpty();
		assertThat(sender.withButtons.get(0).chatId()).isEqualTo(7L);
		assertThat(sender.withButtons.get(0).text()).isEqualTo("Send the report is due in 10 minutes. Time to start.");
		Task stored = repository.tasks.get(task.id());
		assertThat(stored.lastReminderStage()).isEqualTo((short) 1);
		assertThat(stored.lastRemindedAt()).isEqualTo(clock.instant());
	}

	@Test
	void theStageIsRecordedOnlyAfterTheSend() {
		Task task = task(Urgency.LOW);
		sender.result = SendResult.failed(FailureReason.TRANSIENT);

		assertThat(service.sendDueReminders()).isZero();

		assertThat(sender.withButtons).hasSize(1);
		assertThat(repository.tasks.get(task.id()).lastReminderStage()).isZero();
		assertThat(repository.tasks.get(task.id()).lastRemindedAt()).isNull();
		assertThat(repository.recorded).isEmpty();
	}

	@Test
	void aFailedSendIsRetriedOnTheNextRunAndThenNotRepeated() {
		Task task = task(Urgency.LOW);
		sender.result = SendResult.failed(FailureReason.RATE_LIMITED);
		service.sendDueReminders();

		sender.result = SendResult.ok(1);
		clock.advance(Duration.ofSeconds(30));
		assertThat(service.sendDueReminders()).isEqualTo(1);
		clock.advance(Duration.ofSeconds(30));
		assertThat(service.sendDueReminders()).isZero();

		assertThat(sender.withButtons).hasSize(2);
		assertThat(repository.tasks.get(task.id()).lastReminderStage()).isEqualTo((short) 1);
	}

	@Test
	void oneTaskThrowingDoesNotStopTheOthers() {
		Task broken = repository.add(1L, "Broken", Urgency.HIGH, TaskStatus.PENDING, DEADLINE.minusSeconds(60));
		Task fine = task(Urgency.HIGH);
		RecordingSender throwingFirst = new RecordingSender() {
			@Override
			public SendResult sendWithButtons(long chatId, String text, List<List<Button>> buttons) {
				if (chatId == 1L) {
					throw new IllegalStateException("boom");
				}
				return super.sendWithButtons(chatId, text, buttons);
			}
		};
		service = new SendDueRemindersService(repository, throwingFirst,
				new ReminderMessageFactory(TestMessages.of("en")), ReminderPolicy.defaults(), clock);

		try (LogCapture logs = new LogCapture(SendDueRemindersService.class)) {
			assertThat(service.sendDueReminders()).isEqualTo(1);

			assertThat(logs.events(Level.ERROR)).hasSize(1);
			assertThat(logs.allText()).doesNotContain("Broken").doesNotContain("Send the report");
		}
		assertThat(throwingFirst.withButtons).hasSize(1);
		assertThat(repository.tasks.get(fine.id()).lastReminderStage()).isEqualTo((short) 1);
		assertThat(repository.tasks.get(broken.id()).lastReminderStage()).isZero();
	}

	@Test
	void oneMessagePerTaskPerRunEvenWhenSeveralStagesAreDue() {
		Task task = task(Urgency.HIGH);
		clock.advance(TEN.plusMinutes(1));

		assertThat(service.sendDueReminders()).isEqualTo(1);
		assertThat(sender.withButtons).hasSize(1);
		assertThat(repository.tasks.get(task.id()).lastReminderStage()).isEqualTo((short) 3);

		assertThat(service.sendDueReminders()).isZero();
		assertThat(sender.withButtons).hasSize(1);
	}

	@Test
	void tasksOutsideTheWindowOrNotPendingGetNothing() {
		clock.advance(Duration.ofSeconds(-1));
		task(Urgency.HIGH);
		clock.advance(Duration.ofSeconds(1));
		repository.add(7L, "Done", Urgency.HIGH, TaskStatus.DONE, DEADLINE);
		repository.add(7L, "Canceled", Urgency.HIGH, TaskStatus.CANCELED, DEADLINE);
		repository.add(7L, "Far", Urgency.HIGH, TaskStatus.PENDING, DEADLINE.plus(Duration.ofMinutes(1)));

		clock.advance(Duration.ofSeconds(-1));
		assertThat(service.sendDueReminders()).isZero();
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void aTaskThatBecameNonPendingAfterTheListWasLoadedIsSkipped() {
		Task task = task(Urgency.HIGH);
		InMemoryTaskRepository stale = new InMemoryTaskRepository() {
			@Override
			public List<Task> findReminderCandidates(Instant deadlineBefore) {
				List<Task> candidates = repository.findReminderCandidates(deadlineBefore);
				repository.tasks.put(task.id(), Task.restore(task.id(), task.chatId(), task.title(), task.urgency(),
						TaskStatus.DONE, null, task.deadlineAt(), (short) 0, null, task.createdAt(),
						task.updatedAt()));
				return candidates;
			}

			@Override
			public Optional<Task> findById(UUID id) {
				return repository.findById(id);
			}
		};
		service = new SendDueRemindersService(stale, sender, new ReminderMessageFactory(TestMessages.of("en")),
				ReminderPolicy.defaults(), clock);

		assertThat(service.sendDueReminders()).isZero();
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void aRecordRefusedBecauseTheTaskChangedLeavesTheStoredValuesAlone() {
		Task task = task(Urgency.HIGH);
		InMemoryTaskRepository racing = new InMemoryTaskRepository() {
			@Override
			public List<Task> findReminderCandidates(Instant deadlineBefore) {
				return repository.findReminderCandidates(deadlineBefore);
			}

			@Override
			public Optional<Task> findById(UUID id) {
				return repository.findById(id);
			}

			@Override
			public boolean markReminded(UUID id, int stage, Instant at, Instant expectedDeadline) {
				repository.tasks.put(id, Task.restore(task.id(), task.chatId(), task.title(), task.urgency(),
						TaskStatus.DONE, null, task.deadlineAt(), (short) 0, null, task.createdAt(),
						task.updatedAt()));
				return repository.markReminded(id, stage, at, expectedDeadline);
			}
		};
		service = new SendDueRemindersService(racing, sender, new ReminderMessageFactory(TestMessages.of("en")),
				ReminderPolicy.defaults(), clock);

		service.sendDueReminders();

		assertThat(repository.tasks.get(task.id()).lastReminderStage()).isZero();
		assertThat(repository.tasks.get(task.id()).lastRemindedAt()).isNull();
	}

	private Task overdueHigh(int stage, Instant remindedAt) {
		Task task = repository.add(7L, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);
		Task stored = Task.restore(task.id(), task.chatId(), task.title(), task.urgency(), task.status(), null,
				DEADLINE, (short) stage, remindedAt, task.createdAt(), remindedAt);
		repository.tasks.put(task.id(), stored);
		return stored;
	}

	@Test
	void everyReminderKindCarriesTheDoneAndSnoozeButtons() {
		Task scheduled = task(Urgency.HIGH);
		service.sendDueReminders();
		clock.advance(Duration.ofMinutes(12));
		Task overdue = overdueHigh(3, clock.instant().minus(Duration.ofMinutes(2)));
		Task finalOne = overdueHigh(13, clock.instant().minus(Duration.ofMinutes(2)));
		service.sendDueReminders();

		assertThat(sender.withButtons).hasSize(4);
		assertThat(sender.withButtons).allSatisfy(sent -> {
			assertThat(sent.buttons()).hasSize(1);
			assertThat(sent.buttons().get(0)).extracting(Button::text).containsExactly("Done \u2705", "Snooze 10 min");
		});
		assertThat(sender.withButtons.get(0).buttons().get(0).get(0).callbackData())
				.isEqualTo("task:done:" + scheduled.id());
		assertThat(sender.withButtons).extracting(sent -> sent.text())
				.anyMatch(text -> text.contains("OVERDUE")).anyMatch(text -> text.contains("stop reminding"));
		assertThat(overdue.id()).isNotEqualTo(finalOne.id());
	}

	@Test
	void anOverdueRepeatIsSentOncePerRunAndOnlyOnceTheIntervalPassed() {
		clock.advance(TEN);
		Task task = overdueHigh(3, DEADLINE);

		clock.advance(Duration.ofSeconds(119));
		assertThat(service.sendDueReminders()).isZero();
		clock.advance(Duration.ofSeconds(1));
		assertThat(service.sendDueReminders()).isEqualTo(1);
		assertThat(service.sendDueReminders()).isZero();

		Task stored = repository.tasks.get(task.id());
		assertThat(stored.lastReminderStage()).isEqualTo((short) 4);
		assertThat(stored.lastRemindedAt()).isEqualTo(clock.instant());
		assertThat(sender.withButtons).hasSize(1);
	}

	@Test
	void aFailedOverdueSendIsRetriedWithTheStoredStateUnchanged() {
		clock.advance(TEN.plusMinutes(3));
		Task task = overdueHigh(5, DEADLINE.plus(Duration.ofMinutes(1)));
		sender.result = SendResult.failed(FailureReason.TRANSIENT);

		assertThat(service.sendDueReminders()).isZero();

		assertThat(repository.tasks.get(task.id()).lastReminderStage()).isEqualTo((short) 5);
		assertThat(repository.tasks.get(task.id()).lastRemindedAt()).isEqualTo(DEADLINE.plus(Duration.ofMinutes(1)));
		sender.result = SendResult.ok(2);
		clock.advance(Duration.ofSeconds(30));
		assertThat(service.sendDueReminders()).isEqualTo(1);
		assertThat(repository.tasks.get(task.id()).lastReminderStage()).isEqualTo((short) 6);
	}

	@Test
	void aSnoozeDuringTheSendMeansTheSentStageIsNotRecorded() {
		clock.advance(TEN.plusMinutes(10));
		Task task = overdueHigh(5, DEADLINE.plus(Duration.ofMinutes(1)));
		RecordingSender snoozingSender = new RecordingSender() {
			@Override
			public SendResult sendWithButtons(long chatId, String text, List<List<Button>> buttons) {
				repository.snooze(task.id(), DEADLINE, clock.instant().plus(TEN), clock.instant());
				return super.sendWithButtons(chatId, text, buttons);
			}
		};
		service = new SendDueRemindersService(repository, snoozingSender,
				new ReminderMessageFactory(TestMessages.of("en")), ReminderPolicy.defaults(), clock);

		service.sendDueReminders();

		Task stored = repository.tasks.get(task.id());
		assertThat(stored.lastReminderStage()).isZero();
		assertThat(stored.deadlineAt()).isEqualTo(clock.instant().plus(TEN));
	}

	@Test
	void lowAndMediumTasksAreNeverNaggedAfterTheirLastStage() {
		Task low = task(Urgency.LOW);
		Task medium = task(Urgency.MEDIUM);
		service.sendDueReminders();
		clock.advance(TEN);
		service.sendDueReminders();
		int sent = sender.withButtons.size();

		clock.advance(Duration.ofHours(2));

		assertThat(service.sendDueReminders()).isZero();
		assertThat(sender.withButtons).hasSize(sent);
		assertThat(repository.tasks.get(low.id()).lastReminderStage()).isEqualTo((short) 1);
		assertThat(repository.tasks.get(medium.id()).lastReminderStage()).isEqualTo((short) 2);
	}
}
