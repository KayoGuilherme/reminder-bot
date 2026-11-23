package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.TestTasks;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskServiceTest extends AbstractIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final Instant DEADLINE = Instant.parse("2026-11-22T21:00:00Z");

	@Autowired
	TaskService repository;

	private static TaskResponse task(UUID id, Instant start) {
		return TestTasks.create(id, 42L, "send report", Urgency.HIGH, start, DEADLINE, NOW);
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
		TaskResponse saved = task(id, NOW.plusSeconds(60));
		repository.save(saved);

		TaskResponse loaded = repository.findById(id).orElseThrow();
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

		TaskResponse other = TestTasks.create(id, 99L, "different", Urgency.LOW, null, DEADLINE.plusSeconds(60), NOW);
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
		return repository.findReminderCandidates(cutoff, ReminderPolicy.defaults().finalStages()).stream().map(TaskResponse::id).toList();
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

		TaskResponse task = repository.findById(id).orElseThrow();
		assertThat(task.lastReminderStage()).isEqualTo((short) 2);
		assertThat(task.lastRemindedAt()).isEqualTo(NOW);
		assertThat(task.updatedAt()).isEqualTo(NOW);
	}

	@Test
	void markRemindedIsRefusedForAStageNotAboveTheStoredOne() {
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW, 2);

		assertThat(repository.markReminded(id, 2, NOW, NOW)).isFalse();
		assertThat(repository.markReminded(id, 1, NOW, NOW)).isFalse();

		TaskResponse task = repository.findById(id).orElseThrow();
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

		TaskResponse task = repository.findById(id).orElseThrow();
		assertThat(task.lastReminderStage()).isZero();
		assertThat(task.deadlineAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
	}

	@Test
	void markDoneAppliesOnceToAPendingTask() {
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW, 4);

		assertThat(repository.markDone(id, NOW)).isTrue();
		assertThat(repository.markDone(id, NOW.plusSeconds(5))).isFalse();

		TaskResponse task = repository.findById(id).orElseThrow();
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

		TaskResponse task = repository.findById(id).orElseThrow();
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

	private UUID insertFor(long chatId, TaskStatus status, Instant deadline, Instant updatedAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into tasks (id, chat_id, title, urgency, status, deadline_at, updated_at)
				values (?, ?, 'listed', 'LOW', ?, ?, ?)
				""", id, chatId, status.name(), Timestamp.from(deadline), Timestamp.from(updatedAt));
		return id;
	}

	@Test
	void pendingTasksOfAChatComeEarliestDeadlineFirstWithOverdueAndTiesStable() {
		jdbc.update("delete from tasks");
		UUID later = insertFor(42, TaskStatus.PENDING, NOW.plus(Duration.ofHours(5)), NOW);
		UUID overdue = insertFor(42, TaskStatus.PENDING, NOW.minus(Duration.ofHours(1)), NOW);
		UUID tieA = insertFor(42, TaskStatus.PENDING, NOW.plus(Duration.ofHours(1)), NOW);
		UUID tieB = insertFor(42, TaskStatus.PENDING, NOW.plus(Duration.ofHours(1)), NOW);
		insertFor(42, TaskStatus.DONE, NOW.minus(Duration.ofHours(3)), NOW);
		insertFor(42, TaskStatus.CANCELED, NOW.minus(Duration.ofHours(2)), NOW);
		insertFor(7, TaskStatus.PENDING, NOW.minus(Duration.ofHours(4)), NOW);

		List<UUID> ids = repository.findPendingByChat(42, 20).stream().map(TaskResponse::id).toList();

		List<UUID> ties = tieA.toString().compareTo(tieB.toString()) < 0 ? List.of(tieA, tieB) : List.of(tieB, tieA);
		assertThat(ids).containsExactly(overdue, ties.get(0), ties.get(1), later);
		assertThat(repository.countPendingByChat(42)).isEqualTo(4);
		assertThat(repository.countPendingByChat(7)).isEqualTo(1);
		assertThat(repository.countPendingByChat(99)).isZero();
	}

	@Test
	void pendingListHonorsTheLimit() {
		jdbc.update("delete from tasks");
		UUID first = insertFor(42, TaskStatus.PENDING, NOW.plusSeconds(10), NOW);
		UUID second = insertFor(42, TaskStatus.PENDING, NOW.plusSeconds(20), NOW);
		insertFor(42, TaskStatus.PENDING, NOW.plusSeconds(30), NOW);

		assertThat(repository.findPendingByChat(42, 2).stream().map(TaskResponse::id).toList())
				.containsExactly(first, second);
		assertThat(repository.countPendingByChat(42)).isEqualTo(3);
	}

	@Test
	void markCanceledAppliesOnceToAPendingTask() {
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW, 4);

		assertThat(repository.markCanceled(id, NOW)).isTrue();
		assertThat(repository.markCanceled(id, NOW.plusSeconds(5))).isFalse();

		TaskResponse task = repository.findById(id).orElseThrow();
		assertThat(task.status()).isEqualTo(TaskStatus.CANCELED);
		assertThat(task.updatedAt()).isEqualTo(NOW);
		assertThat(task.lastReminderStage()).isEqualTo((short) 4);
	}

	@Test
	void markCanceledIsRefusedForDoneAndUnknownTasks() {
		UUID done = insert(Urgency.HIGH, TaskStatus.DONE, NOW, 0);

		assertThat(repository.markCanceled(done, NOW)).isFalse();
		assertThat(repository.markCanceled(UUID.randomUUID(), NOW)).isFalse();
		assertThat(repository.findById(done).orElseThrow().status()).isEqualTo(TaskStatus.DONE);
	}

	@Test
	void aCanceledTaskIsNoLongerAReminderCandidate() {
		jdbc.update("delete from tasks");
		UUID id = insert(Urgency.HIGH, TaskStatus.PENDING, NOW.plus(Duration.ofMinutes(5)), 0);
		repository.markCanceled(id, NOW);

		assertThat(candidateIds(NOW.plus(Duration.ofMinutes(10)))).isEmpty();
	}

	@Test
	void deleteFinishedBeforeRemovesOnlyOldDoneAndCanceledTasks() {
		jdbc.update("delete from tasks");
		Instant cutoff = NOW.minus(Duration.ofDays(30));
		UUID oldDone = insertFor(42, TaskStatus.DONE, NOW, cutoff.minusSeconds(1));
		UUID oldCanceled = insertFor(42, TaskStatus.CANCELED, NOW, cutoff.minus(Duration.ofDays(15)));
		UUID atBoundary = insertFor(42, TaskStatus.DONE, NOW, cutoff);
		UUID recentDone = insertFor(42, TaskStatus.DONE, NOW, NOW.minus(Duration.ofDays(29)));
		UUID oldPending = insertFor(42, TaskStatus.PENDING, NOW, cutoff.minus(Duration.ofDays(170)));

		assertThat(repository.deleteFinishedBefore(cutoff)).isEqualTo(2);

		assertThat(repository.findById(oldDone)).isEmpty();
		assertThat(repository.findById(oldCanceled)).isEmpty();
		assertThat(repository.findById(atBoundary)).isPresent();
		assertThat(repository.findById(recentDone)).isPresent();
		assertThat(repository.findById(oldPending)).isPresent();
		assertThat(repository.deleteFinishedBefore(cutoff)).isZero();
	}

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
