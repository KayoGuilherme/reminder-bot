package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.component.ReminderMessageFactory;
import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.enums.SendFailureReason;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

class SendDueRemindersIntegrationTest extends AbstractIntegrationTest {

	private static final Instant START = Instant.parse("2026-11-22T14:48:00Z");
	private static final Instant DEADLINE = START.plus(Duration.ofMinutes(12));
	private static final long CHAT = 4242L;

	@Autowired
	TaskService repository;

	private final MessageSender sender = mock(MessageSender.class);
	private MutableClock clock;
	private SendDueRemindersService job;
	private ReminderActionService actions;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from tasks");
		clock = new MutableClock(START);
		when(sender.sendWithButtons(anyLong(), anyString(), anyList())).thenReturn(SendResultResponse.ok(1));
		when(sender.sendText(anyLong(), anyString())).thenReturn(SendResultResponse.ok(2));
		when(sender.editText(anyLong(), anyInt(), anyString())).thenReturn(SendResultResponse.ok(9));
		job = newJob(sender);
		actions = new ReminderActionService(repository, sender, TestMessages.of("en"),
				TestProperties.of(ZoneOffset.UTC, "en"), clock);
	}

	private SendDueRemindersService newJob(MessageSender port) {
		return new SendDueRemindersService(repository, port, new ReminderMessageFactory(TestMessages.of("en")),
				ReminderPolicy.defaults(), clock);
	}

	private UUID insert(long chat, String title, Urgency urgency, TaskStatus status, Instant deadline) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into tasks (id, chat_id, title, urgency, status, deadline_at)
				values (?, ?, ?, ?, ?, ?)
				""", id, chat, title, urgency.name(), status.name(), Timestamp.from(deadline));
		return id;
	}

	private int stage(UUID id) {
		return repository.findById(id).orElseThrow().lastReminderStage();
	}

	private List<Instant> tickUntil(Instant until) {
		List<Instant> sentAt = new ArrayList<>();
		while (!clock.instant().isAfter(until)) {
			if (job.sendDueReminders() > 0) {
				sentAt.add(clock.instant());
			}
			clock.advance(Duration.ofSeconds(30));
		}
		return sentAt;
	}

	@Test
	void aHighTaskDueIn12MinutesIsRemindedExactlyAtT10T5AndT0() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);

		List<Instant> sentAt = tickUntil(DEADLINE.plus(Duration.ofMinutes(1)));

		ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
		verify(sender, times(3)).sendWithButtons(eq(CHAT), texts.capture(), anyList());
		assertThat(sentAt).containsExactly(DEADLINE.minus(Duration.ofMinutes(10)),
				DEADLINE.minus(Duration.ofMinutes(5)), DEADLINE);
		assertThat(texts.getAllValues().get(0)).isEqualTo(
				"Send the report is due in 10 MINUTES. Drop everything and do it now.");
		assertThat(texts.getAllValues().get(1)).contains("5 MINUTES");
		assertThat(texts.getAllValues().get(2)).contains("0 MINUTES");
		TaskResponse stored = repository.findById(id).orElseThrow();
		assertThat(stored.lastReminderStage()).isEqualTo((short) 3);
		assertThat(stored.lastRemindedAt()).isEqualTo(DEADLINE);
	}

	@Test
	void aFailedSendKeepsTheStageAndTheNextTickRetries() {
		UUID id = insert(CHAT, "Send the report", Urgency.LOW, TaskStatus.PENDING, DEADLINE);
		clock.advance(Duration.ofMinutes(2));
		when(sender.sendWithButtons(anyLong(), anyString(), anyList())).thenReturn(SendResultResponse.failed(SendFailureReason.TRANSIENT));

		assertThat(job.sendDueReminders()).isZero();
		assertThat(stage(id)).isZero();
		assertThat(repository.findById(id).orElseThrow().lastRemindedAt()).isNull();

		when(sender.sendWithButtons(anyLong(), anyString(), anyList())).thenReturn(SendResultResponse.ok(2));
		clock.advance(Duration.ofSeconds(30));
		assertThat(job.sendDueReminders()).isEqualTo(1);
		assertThat(stage(id)).isEqualTo(1);
	}

	@Test
	void aRestartDoesNotRepeatWhatWasAlreadySent() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);
		clock.advance(Duration.ofMinutes(2));
		assertThat(job.sendDueReminders()).isEqualTo(1);

		MessageSender afterRestart = mock(MessageSender.class);
		SendDueRemindersService restarted = newJob(afterRestart);
		clock.advance(Duration.ofSeconds(30));

		assertThat(restarted.sendDueReminders()).isZero();
		verify(afterRestart, never()).sendWithButtons(anyLong(), anyString(), anyList());
		assertThat(stage(id)).isEqualTo(1);
	}

	@Test
	void doneAndCanceledTasksGetNothing() {
		insert(CHAT, "Done", Urgency.HIGH, TaskStatus.DONE, DEADLINE);
		insert(CHAT, "Canceled", Urgency.HIGH, TaskStatus.CANCELED, DEADLINE);

		tickUntil(DEADLINE.plus(Duration.ofMinutes(1)));

		verify(sender, never()).sendWithButtons(anyLong(), anyString(), anyList());
	}

	@Test
	void aTaskCreatedInsideTheWindowIsRemindedOnTheNextTick() {
		clock.advance(Duration.ofMinutes(5));
		UUID id = insert(CHAT, "Late one", Urgency.HIGH, TaskStatus.PENDING, clock.instant().plus(Duration.ofMinutes(7)));

		assertThat(job.sendDueReminders()).isEqualTo(1);

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(sender).sendWithButtons(eq(CHAT), text.capture(), anyList());
		assertThat(text.getValue()).contains("7 MINUTES");
		assertThat(stage(id)).isEqualTo(1);
	}

	@Test
	void afterDowntimeOnlyTheHighestDueStageIsSentOnce() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);
		clock.advance(Duration.ofMinutes(20));

		assertThat(job.sendDueReminders()).isEqualTo(1);
		assertThat(job.sendDueReminders()).isZero();

		verify(sender).sendWithButtons(anyLong(), anyString(), anyList());
		assertThat(stage(id)).isEqualTo(3);
	}

	@Test
	void oneTaskFailingDoesNotBlockAnother() {
		UUID broken = insert(1L, "Broken", Urgency.HIGH, TaskStatus.PENDING, DEADLINE.minus(Duration.ofMinutes(1)));
		UUID fine = insert(2L, "Fine", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);
		clock.advance(Duration.ofMinutes(2));
		when(sender.sendWithButtons(eq(1L), anyString(), anyList())).thenThrow(new IllegalStateException("boom"));

		assertThat(job.sendDueReminders()).isEqualTo(1);

		assertThat(stage(broken)).isZero();
		assertThat(stage(fine)).isEqualTo(1);
	}

	private List<String> sentTexts() {
		ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
		verify(sender, org.mockito.Mockito.atLeast(0)).sendWithButtons(eq(CHAT), texts.capture(), anyList());
		return texts.getAllValues();
	}

	private static List<Instant> every(Instant first, Duration step, int count) {
		return java.util.stream.IntStream.range(0, count).mapToObj(i -> first.plus(step.multipliedBy(i))).toList();
	}

	@Test
	void anIgnoredHighTaskIsNaggedEveryTwoMinutesUpToTheCapThenOnceMoreThenNeverAgain() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);

		List<Instant> sentAt = tickUntil(DEADLINE.plus(Duration.ofMinutes(40)));

		List<Instant> expected = new ArrayList<>(List.of(DEADLINE.minus(Duration.ofMinutes(10)),
				DEADLINE.minus(Duration.ofMinutes(5)), DEADLINE));
		expected.addAll(every(DEADLINE.plus(Duration.ofMinutes(2)), Duration.ofMinutes(2), 10));
		expected.add(DEADLINE.plus(Duration.ofMinutes(22)));
		assertThat(sentAt).containsExactlyElementsOf(expected);
		List<String> texts = sentTexts();
		assertThat(texts).hasSize(14);
		assertThat(texts.get(3)).contains("OVERDUE").contains("2 MINUTES");
		assertThat(texts.get(13)).contains("stop reminding").contains("22 minutes");
		assertThat(stage(id)).isEqualTo(14);
	}

	@Test
	void everyReminderCarriesTheDoneAndSnoozeButtons() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);

		tickUntil(DEADLINE.plus(Duration.ofMinutes(3)));

		ArgumentCaptor<List<List<com.kayo.reminder.application.dto.response.ButtonResponse>>> buttons = ArgumentCaptor
				.forClass(List.class);
		verify(sender, times(4)).sendWithButtons(eq(CHAT), anyString(), buttons.capture());
		assertThat(buttons.getAllValues()).allSatisfy(rows -> assertThat(rows.get(0))
				.extracting(b -> b.callbackData()).containsExactly("task:done:" + id, "task:snooze:" + id));
	}

	@Test
	void pressingDoneStopsTheNaggingImmediately() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);
		tickUntil(DEADLINE.plus(Duration.ofMinutes(5)));
		int sentBefore = sentTexts().size();

		actions.done(CHAT, 9, id);
		tickUntil(DEADLINE.plus(Duration.ofMinutes(40)));

		assertThat(sentBefore).isEqualTo(5);
		assertThat(sentTexts()).hasSize(5);
		assertThat(repository.findById(id).orElseThrow().status()).isEqualTo(TaskStatus.DONE);
		verify(sender).editText(eq(CHAT), eq(9), contains("Send the report"));
	}

	@Test
	void snoozeOfAnOverdueTaskRestartsTheCycleAtTheNextRun() {
		UUID id = insert(CHAT, "Send the report", Urgency.HIGH, TaskStatus.PENDING, DEADLINE);
		tickUntil(DEADLINE.plus(Duration.ofMinutes(5)));
		assertThat(stage(id)).isEqualTo(5);
		int sentBefore = sentTexts().size();
		Instant snoozedAt = clock.instant();

		actions.snooze(CHAT, 9, id);
		actions.snooze(CHAT, 9, id);

		TaskResponse snoozed = repository.findById(id).orElseThrow();
		assertThat(snoozed.deadlineAt()).isEqualTo(snoozedAt.plus(Duration.ofMinutes(10)));
		assertThat(snoozed.lastReminderStage()).isZero();
		verify(sender, times(1)).editText(eq(CHAT), eq(9), contains("Snoozed"));
		verify(sender, times(1)).sendText(eq(CHAT), contains("already snoozed"));

		List<Instant> sentAt = tickUntil(snoozed.deadlineAt().plus(Duration.ofMinutes(3)));

		assertThat(sentAt).containsExactly(snoozedAt, snoozedAt.plus(Duration.ofMinutes(5)),
				snoozedAt.plus(Duration.ofMinutes(10)), snoozedAt.plus(Duration.ofMinutes(12)));
		List<String> after = sentTexts().subList(sentBefore, sentTexts().size());
		assertThat(after.get(0)).contains("10 MINUTES");
		assertThat(after.get(1)).contains("5 MINUTES");
		assertThat(after.get(2)).contains("0 MINUTES");
		assertThat(after.get(3)).contains("OVERDUE");
		assertThat(stage(id)).isEqualTo(4);
	}
}
