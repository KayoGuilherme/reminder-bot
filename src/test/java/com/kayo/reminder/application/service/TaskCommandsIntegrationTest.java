package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.port.in.CreateTaskFromVoiceUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.ReminderPolicy;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** /tasks, Done and Cancel from the list, and the reminder job, against a real PostgreSQL. */
class TaskCommandsIntegrationTest extends AbstractIntegrationTest {

	private static final long CHAT = 4242L;
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");

	@Autowired
	TaskRepository repository;

	private final RecordingSender sender = new RecordingSender();
	private MutableClock clock;
	private IncomingCommandService commands;
	private SendDueRemindersService reminders;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from tasks");
		clock = new MutableClock(NOW);
		var properties = TestProperties.of(ZoneId.of("America/Fortaleza"), "en");
		var messages = TestMessages.of("en");
		commands = new IncomingCommandService(sender, messages, mock(CreateTaskFromVoiceUseCase.class),
				new ReminderActionService(repository, sender, messages, properties, clock),
				new ListTasksService(repository, sender, messages, properties, clock));
		reminders = new SendDueRemindersService(repository, sender, new ReminderMessageFactory(messages),
				ReminderPolicy.defaults(), clock);
	}

	private UUID insert(String title, TaskStatus status, Duration fromNow) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into tasks (id, chat_id, title, urgency, status, deadline_at) values (?, ?, ?, 'HIGH', ?, ?)",
				id, CHAT, title, status.name(), Timestamp.from(NOW.plus(fromNow)));
		return id;
	}

	private String status(UUID id) {
		return jdbc.queryForObject("select status from tasks where id = ?", String.class, id);
	}

	private void press(int messageId, String data) {
		commands.handle(IncomingCommand.callback(CHAT, 42, messageId, "cb-" + messageId, data));
	}

	@Test
	void listThenCancelAndDoneFromTheListStopsTheRemindersOfThoseTasks() {
		UUID soon = insert("second", TaskStatus.PENDING, Duration.ofMinutes(20));
		UUID first = insert("first", TaskStatus.PENDING, Duration.ofMinutes(5));
		UUID kept = insert("third", TaskStatus.PENDING, Duration.ofMinutes(30));
		insert("old", TaskStatus.DONE, Duration.ofMinutes(1));

		commands.handle(IncomingCommand.text(CHAT, 42, 1, "/tasks"));

		assertThat(sender.withButtons).hasSize(3);
		assertThat(sender.withButtons).extracting(m -> m.text().lines().findFirst().orElseThrow())
				.satisfiesExactly(a -> assertThat(a).endsWith("first"), b -> assertThat(b).endsWith("second"),
						c -> assertThat(c).endsWith("third"));
		assertThat(sender.withButtons.get(0).text()).contains("2026-11-22 10:05");

		String cancelFirst = sender.withButtons.get(0).buttons().get(0).get(1).callbackData();
		String doneSecond = sender.withButtons.get(1).buttons().get(0).get(0).callbackData();
		press(11, cancelFirst);
		press(12, doneSecond);

		assertThat(status(first)).isEqualTo("CANCELED");
		assertThat(status(soon)).isEqualTo("DONE");
		assertThat(status(kept)).isEqualTo("PENDING");
		assertThat(sender.edits).hasSize(2);
		assertThat(sender.edits.get(0).text()).startsWith("Canceled").contains("first");
		assertThat(sender.edits.get(1).text()).startsWith("Done").contains("second");

		sender.withButtons.clear();
		clock.advance(Duration.ofMinutes(25));
		reminders.sendDueReminders();

		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("third");

		sender.withButtons.clear();
		sender.texts.clear();
		commands.handle(IncomingCommand.text(CHAT, 42, 2, "/tasks"));
		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("third");
	}

	@Test
	void whenNothingIsPendingTheOwnerGetsTheFriendlyMessage() {
		insert("done", TaskStatus.DONE, Duration.ofMinutes(5));
		insert("canceled", TaskStatus.CANCELED, Duration.ofMinutes(5));

		commands.handle(IncomingCommand.text(CHAT, 42, 1, "/tasks"));

		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(TestMessages.text("en", "tasks.empty"));
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void pressingCancelTwiceOrAfterDoneChangesNothingMore() {
		UUID id = insert("report", TaskStatus.PENDING, Duration.ofMinutes(5));
		String cancel = "task:cancel:" + id;

		press(1, cancel);
		press(2, cancel);
		press(3, "task:done:" + id);

		assertThat(status(id)).isEqualTo("CANCELED");
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(
				TestMessages.text("en", "tasks.action.already-canceled"),
				TestMessages.text("en", "reminder.action.not-active"));
	}
}
