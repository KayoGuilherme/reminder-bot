package com.kayo.reminder.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Booting this context with {@code ddl-auto=validate} also proves the mapping matches V1 (no new migration). */
class JpaTaskRepositoryTest extends AbstractIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final Instant DEADLINE = Instant.parse("2026-11-22T21:00:00Z");

	@Autowired
	TaskRepository repository;

	private static Task task(UUID id, Instant start) {
		return Task.create(id, 42L, "send report", Urgency.HIGH, start, DEADLINE, NOW);
	}

	@Test
	void savesAPendingTaskAsOneRow() {
		UUID id = UUID.randomUUID();
		repository.save(task(id, null));

		Map<String, Object> row = jdbc.queryForMap("select * from tasks where id = ?", id);
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("urgency")).isEqualTo("HIGH");
		assertThat(((Number) row.get("chat_id")).longValue()).isEqualTo(42L);
		assertThat(((Number) row.get("last_reminder_stage")).intValue()).isZero();
		assertThat(row.get("start_at")).isNull();
		assertThat(Instant.ofEpochMilli(((java.util.Date) row.get("deadline_at")).getTime())).isEqualTo(DEADLINE);
		assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void roundTripsEveryField() {
		UUID id = UUID.randomUUID();
		Task saved = task(id, NOW.plusSeconds(60));
		repository.save(saved);

		Task loaded = repository.findById(id).orElseThrow();
		assertThat(loaded).isEqualTo(saved);
		assertThat(loaded.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(loaded.startAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void unknownIdIsEmpty() {
		assertThat(repository.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void savingTheSameIdTwiceFailsAndKeepsTheFirstRow() {
		UUID id = UUID.randomUUID();
		repository.save(task(id, null));

		Task other = Task.create(id, 99L, "different", Urgency.LOW, null, DEADLINE.plusSeconds(60), NOW);
		assertThatThrownBy(() -> repository.save(other)).isInstanceOf(RuntimeException.class);

		assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);
		assertThat(repository.findById(id).orElseThrow().title()).isEqualTo("send report");
	}

	private UUID insert(Urgency urgency, TaskStatus status, Instant deadline, int stage) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into tasks (id, chat_id, title, urgency, status, deadline_at, last_reminder_stage)
				values (?, 42, 'candidate', ?, ?, ?, ?)
				""", id, urgency.name(), status.name(), Timestamp.from(deadline), stage);
		return id;
	}

	private List<UUID> candidateIds(Instant cutoff) {
		return repository.findReminderCandidates(cutoff).stream().map(Task::id).toList();
	}

	@Test
	void candidatesAreOnlyPendingTasksInTheWindowWithStagesLeft() {
		jdbc.update("delete from tasks");
		Instant cutoff = NOW.plus(Duration.ofMinutes(10));
		UUID inWindow = insert(Urgency.HIGH, TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(8)), 0);
		UUID exactlyAtCutoff = insert(Urgency.LOW, TaskStatus.PENDING, cutoff, 0);
		UUID pastDeadline = insert(Urgency.MEDIUM, TaskStatus.PENDING, NOW.minus(Duration.ofHours(2)), 1);
		insert(Urgency.HIGH, TaskStatus.PENDING, cutoff.plusSeconds(1), 0);
		insert(Urgency.HIGH, TaskStatus.DONE, NOW.plus(Duration.ofMinutes(5)), 0);
		insert(Urgency.HIGH, TaskStatus.CANCELED, NOW.plus(Duration.ofMinutes(5)), 0);
		insert(Urgency.LOW, TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(5)), 1);
		insert(Urgency.MEDIUM, TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(5)), 2);
		insert(Urgency.HIGH, TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(5)), 14);

		assertThat(candidateIds(cutoff)).containsExactly(pastDeadline, inWindow, exactlyAtCutoff);
	}

	@Test
	void overdueHighTasksStayCandidatesUntilTheFinalStage() {
		jdbc.update("delete from tasks");
		Instant overdue = NOW.minus(Duration.ofHours(1));
		UUID afterStageThree = insert(Urgency.HIGH, TaskStatus.PENDING, overdue, 3);
		UUID afterLastRepeat = insert(Urgency.HIGH, TaskStatus.PENDING, overdue.plusSeconds(1), 13);
		insert(Urgency.HIGH, TaskStatus.PENDING, overdue.plusSeconds(2), 14);

		assertThat(candidateIds(NOW)).containsExactly(afterStageThree, afterLastRepeat);
	}

	@Test
	void markRemindedRaisesTheStageAndRecordsWhen() {
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(5)), 1);

		assertThat(repository.markReminded(id, 2, NOW, NOW.plus(Duration.ofMinutes(5)))).isTrue();

		Task task = repository.findById(id).orElseThrow();
		assertThat(task.lastReminderStage()).isEqualTo((short) 2);
		assertThat(task.lastRemindedAt()).isEqualTo(NOW);
		assertThat(task.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void markRemindedIsRefusedForAStageNotAboveTheStoredOne() {
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW, 2);

		assertThat(repository.markReminded(id, 2, NOW, NOW)).isFalse();
		assertThat(repository.markReminded(id, 1, NOW, NOW)).isFalse();

		Task task = repository.findById(id).orElseThrow();
		assertThat(task.lastReminderStage()).isEqualTo((short) 2);
		assertThat(task.lastRemindedAt()).isNull();
	}

	@Test
	void markRemindedIsRefusedForANonPendingTask() {
		UUID done = insert(Urgency.HIGH, TaskStatus.DONE, NOW, 0);
		UUID canceled = insert(Urgency.HIGH, TaskStatus.CANCELED, NOW, 0);

		assertThat(repository.markReminded(done, 1, NOW, NOW)).isFalse();
		assertThat(repository.markReminded(canceled, 1, NOW, NOW)).isFalse();

		assertThat(repository.findById(done).orElseThrow().lastReminderStage()).isZero();
		assertThat(repository.findById(canceled).orElseThrow().lastRemindedAt()).isNull();
	}

	@Test
	void markRemindedForAnUnknownTaskIsRefused() {
		assertThat(repository.markReminded(UUID.randomUUID(), 1, NOW, NOW)).isFalse();
	}

	@Test
	void markRemindedIsRefusedWhenTheDeadlineChangedSinceTheDecision() {
		Instant oldDeadline = NOW.minus(Duration.ofMinutes(20));
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, oldDeadline, 5);
		assertThat(repository.snooze(id, oldDeadline, NOW.plus(Duration.ofMinutes(10)), NOW)).isTrue();

		assertThat(repository.markReminded(id, 6, NOW, oldDeadline)).isFalse();

		Task task = repository.findById(id).orElseThrow();
		assertThat(task.lastReminderStage()).isZero();
		assertThat(task.deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
	}

	@Test
	void markDoneAppliesOnceToAPendingTask() {
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW, 4);

		assertThat(repository.markDone(id, NOW)).isTrue();
		assertThat(repository.markDone(id, NOW.plusSeconds(5))).isFalse();

		Task task = repository.findById(id).orElseThrow();
		assertThat(task.status()).isEqualTo(TaskStatus.DONE);
		assertThat(task.updatedAt()).isEqualTo(NOW);
		assertThat(task.lastReminderStage()).isEqualTo((short) 4);
	}

	@Test
	void markDoneIsRefusedForCanceledAndUnknownTasks() {
		UUID canceled = insert(Urgency.HIGH, TaskStatus.CANCELED, NOW, 0);

		assertThat(repository.markDone(canceled, NOW)).isFalse();
		assertThat(repository.markDone(UUID.randomUUID(), NOW)).isFalse();
		assertThat(repository.findById(canceled).orElseThrow().status()).isEqualTo(TaskStatus.CANCELED);
	}

	@Test
	void snoozeMovesTheDeadlineAndResetsTheStageOnce() {
		Instant deadline = NOW.minus(Duration.ofMinutes(20));
		Instant newDeadline = NOW.plus(Duration.ofMinutes(10));
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, deadline, 6);

		assertThat(repository.snooze(id, deadline, newDeadline, NOW)).isTrue();
		assertThat(repository.snooze(id, deadline, newDeadline.plusSeconds(600), NOW)).isFalse();
		assertThat(repository.snooze(id, newDeadline, newDeadline.plusSeconds(600), NOW)).isFalse();

		Task task = repository.findById(id).orElseThrow();
		assertThat(task.deadlineAt()).isEqualTo(newDeadline);
		assertThat(task.lastReminderStage()).isZero();
		assertThat(task.updatedAt()).isEqualTo(NOW);
		assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
	}

	@Test
	void snoozeIsRefusedForAStaleDeadlineAndForFinishedOrUnknownTasks() {
		UUID pending = insert(Urgency.HIGH, TaskStatus.PENDING, NOW, 1);
		UUID done = insert(Urgency.HIGH, TaskStatus.DONE, NOW, 1);
		UUID canceled = insert(Urgency.HIGH, TaskStatus.CANCELED, NOW, 1);

		assertThat(repository.snooze(pending, NOW.minusSeconds(1), NOW.plusSeconds(600), NOW)).isFalse();
		assertThat(repository.snooze(done, NOW, NOW.plusSeconds(600), NOW)).isFalse();
		assertThat(repository.snooze(canceled, NOW, NOW.plusSeconds(600), NOW)).isFalse();
		assertThat(repository.snooze(UUID.randomUUID(), NOW, NOW.plusSeconds(600), NOW)).isFalse();

		assertThat(repository.findById(pending).orElseThrow().deadlineAt()).isEqualTo(NOW);
	}

	/** Not a performance benchmark: it checks the planner is allowed to use the (status, deadline_at) index. */
	@Test
	void candidateQueryCanUseTheStatusDeadlineIndex() {
		jdbc.update("delete from tasks");
		for (int i = 0; i < 300; i++) {
			insert(Urgency.LOW, TaskStatus.DONE, NOW.plus(Duration.ofDays(i + 1)), 0);
		}
		jdbc.execute("analyze tasks");

		List<String> plan = jdbc.queryForList("""
				explain select * from tasks
				where status = 'PENDING' and deadline_at <= now() + interval '10 minutes'
				""", String.class);

		assertThat(String.join("\n", plan)).contains("idx_tasks_status_deadline");
	}
}
