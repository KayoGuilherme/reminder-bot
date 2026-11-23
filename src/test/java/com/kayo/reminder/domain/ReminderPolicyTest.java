package com.kayo.reminder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReminderPolicyTest {

	private static final Instant DEADLINE = Instant.parse("2026-11-22T15:00:00Z");

	private final ReminderPolicy policy = ReminderPolicy.defaults();

	private static Task task(Urgency urgency, TaskStatus status, int lastStage) {
		return Task.restore(UUID.randomUUID(), 1L, "Report", urgency, status, null, DEADLINE, (short) lastStage, null,
				DEADLINE.minus(Duration.ofHours(1)), DEADLINE.minus(Duration.ofHours(1)));
	}

	/** The instant {@code beforeDeadline} before the deadline (negative means after it). */
	private static Instant at(Duration beforeDeadline) {
		return DEADLINE.minus(beforeDeadline);
	}

	private static final Duration SECOND = Duration.ofSeconds(1);

	static Stream<Arguments> table() {
		Duration m10 = Duration.ofMinutes(10);
		Duration m5 = Duration.ofMinutes(5);
		return Stream.of(
				// urgency, last stage, time before deadline, expected stage (0 = nothing)
				Arguments.of(Urgency.LOW, 0, m10.plus(SECOND), 0),
				Arguments.of(Urgency.LOW, 0, m10, 1),
				Arguments.of(Urgency.LOW, 0, m5, 1),
				Arguments.of(Urgency.LOW, 0, Duration.ZERO, 1),
				Arguments.of(Urgency.LOW, 0, SECOND.negated(), 1),
				Arguments.of(Urgency.LOW, 1, Duration.ZERO, 0),
				Arguments.of(Urgency.MEDIUM, 0, m10.plus(SECOND), 0),
				Arguments.of(Urgency.MEDIUM, 0, m10, 1),
				Arguments.of(Urgency.MEDIUM, 1, m5, 0),
				Arguments.of(Urgency.MEDIUM, 1, SECOND, 0),
				Arguments.of(Urgency.MEDIUM, 1, Duration.ZERO, 2),
				Arguments.of(Urgency.MEDIUM, 1, SECOND.negated(), 2),
				Arguments.of(Urgency.MEDIUM, 2, SECOND.negated(), 0),
				Arguments.of(Urgency.HIGH, 0, m10.plus(SECOND), 0),
				Arguments.of(Urgency.HIGH, 0, m10, 1),
				Arguments.of(Urgency.HIGH, 1, m10.minus(SECOND), 0),
				Arguments.of(Urgency.HIGH, 1, m5.plus(SECOND), 0),
				Arguments.of(Urgency.HIGH, 1, m5, 2),
				Arguments.of(Urgency.HIGH, 2, m5.minus(SECOND), 0),
				Arguments.of(Urgency.HIGH, 2, SECOND, 0),
				Arguments.of(Urgency.HIGH, 2, Duration.ZERO, 3),
				Arguments.of(Urgency.HIGH, 2, SECOND.negated(), 3),
				// a task past stage 3 waits for the repeat interval, which these rows (no last reminded time) skip
				Arguments.of(Urgency.HIGH, 3, SECOND.negated(), 4),
				Arguments.of(Urgency.HIGH, 3, m10, 0),
				Arguments.of(Urgency.HIGH, 3, SECOND, 0));
	}

	@ParameterizedTest(name = "{0} after stage {1}, {2} before deadline -> stage {3}")
	@MethodSource("table")
	void decidesTheStageByUrgencyAndTime(Urgency urgency, int lastStage, Duration beforeDeadline, int expected) {
		var decision = policy.decide(task(urgency, TaskStatus.PENDING, lastStage), at(beforeDeadline));

		if (expected == 0) {
			assertThat(decision).isEmpty();
		} else {
			assertThat(decision).map(ReminderDecision::stage).contains(expected);
		}
	}

	@ParameterizedTest
	@MethodSource("nonPending")
	void nonPendingTasksAreNeverReminded(Urgency urgency, TaskStatus status) {
		assertThat(policy.decide(task(urgency, status, 0), at(Duration.ofMinutes(1)))).isEmpty();
		assertThat(policy.decide(task(urgency, status, 0), at(Duration.ZERO))).isEmpty();
	}

	static Stream<Arguments> nonPending() {
		return Stream.of(Urgency.values())
				.flatMap(u -> Stream.of(TaskStatus.DONE, TaskStatus.CANCELED).map(s -> Arguments.of(u, s)));
	}

	@ParameterizedTest
	@MethodSource("urgencies")
	void downtimeSendsOnlyTheHighestDueStage(Urgency urgency) {
		Instant afterDowntime = at(Duration.ofMinutes(1).negated());

		var decision = policy.decide(task(urgency, TaskStatus.PENDING, 0), afterDowntime);

		assertThat(decision).map(ReminderDecision::stage).contains(LAST_SCHEDULED.get(urgency));
	}

	private static final Map<Urgency, Integer> LAST_SCHEDULED = Map.of(Urgency.LOW, 1, Urgency.MEDIUM, 2, Urgency.HIGH, 3);

	static Stream<Urgency> urgencies() {
		return Stream.of(Urgency.values());
	}

	@Test
	void taskCreatedInsideTheWindowGetsStageOne() {
		assertThat(policy.decide(task(Urgency.HIGH, TaskStatus.PENDING, 0), at(Duration.ofMinutes(7))))
				.contains(ReminderDecision.scheduled(1));
	}

	@Test
	void skippedStagesAreNotRevisitedAfterTheCatchUp() {
		Instant now = at(Duration.ofMinutes(1).negated());
		int stage = policy.decide(task(Urgency.HIGH, TaskStatus.PENDING, 0), now).orElseThrow().stage();
		assertThat(stage).isEqualTo(3);

		Task sent = taskReminded(Urgency.HIGH, stage, now);

		assertThat(policy.decide(sent, now)).isEmpty();
		assertThat(policy.decide(sent, now.plus(INTERVAL).minus(SECOND))).isEmpty();
		assertThat(policy.decide(sent, now.plus(INTERVAL)).orElseThrow().stage()).isEqualTo(4);
	}

	@Test
	void catchUpNeverJumpsStraightToAnOverdueRepeat() {
		var decision = policy.decide(task(Urgency.HIGH, TaskStatus.PENDING, 0), at(Duration.ofHours(1).negated()));

		assertThat(decision).contains(ReminderDecision.scheduled(3));
	}

	@Test
	void decidingTwiceAtTheSameInstantGivesTheSameResult() {
		Task task = task(Urgency.HIGH, TaskStatus.PENDING, 1);
		Instant now = at(Duration.ofMinutes(5));

		assertThat(policy.decide(task, now)).isEqualTo(policy.decide(task, now));
	}

	@Test
	void finalStageIsTheLastScheduledOneExceptForHighWhichEndsAfterTheCap() {
		assertThat(policy.finalStage(Urgency.LOW)).isEqualTo(1);
		assertThat(policy.finalStage(Urgency.MEDIUM)).isEqualTo(2);
		assertThat(policy.finalStage(Urgency.HIGH)).isEqualTo(14);
		assertThat(new ReminderPolicy(INTERVAL, 3).finalStage(Urgency.HIGH)).isEqualTo(7);
	}

	@Test
	void rejectsAnInvalidIntervalOrCap() {
		assertThatThrownBy(() -> new ReminderPolicy(Duration.ZERO, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReminderPolicy(INTERVAL, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	// --- HIGH overdue repeats -------------------------------------------------------------------------------

	private static final Duration INTERVAL = Duration.ofMinutes(2);

	/** A task whose {@code lastStage} reminder was sent at {@code remindedAt}. */
	private static Task taskReminded(Urgency urgency, int lastStage, Instant remindedAt) {
		return Task.restore(UUID.randomUUID(), 1L, "Report", urgency, TaskStatus.PENDING, null, DEADLINE,
				(short) lastStage, remindedAt, DEADLINE.minus(Duration.ofHours(1)), remindedAt);
	}

	static Stream<Arguments> repeatTable() {
		Duration late = Duration.ofSeconds(10);
		return Stream.of(
				// last stage, sent at (relative to the deadline), evaluated at, expected stage (0 = nothing)
				Arguments.of(3, Duration.ZERO, Duration.ofSeconds(119), 0),
				Arguments.of(3, Duration.ZERO, INTERVAL, 4),
				Arguments.of(3, Duration.ZERO, INTERVAL.plus(Duration.ofHours(1)), 4),
				// spacing runs from the real send time, not from the deadline
				Arguments.of(4, INTERVAL.plus(late), INTERVAL.multipliedBy(2), 0),
				Arguments.of(4, INTERVAL.plus(late), INTERVAL.multipliedBy(2).plus(late), 5),
				Arguments.of(6, Duration.ofMinutes(10), Duration.ofMinutes(11), 0),
				Arguments.of(6, Duration.ofMinutes(10), Duration.ofMinutes(12), 7),
				// the cap: stage 13 is the tenth overdue reminder, 14 the final one, then silence
				Arguments.of(12, Duration.ofMinutes(22), Duration.ofMinutes(24), 13),
				Arguments.of(13, Duration.ofMinutes(24), Duration.ofMinutes(25), 0),
				Arguments.of(13, Duration.ofMinutes(24), Duration.ofMinutes(26), 14),
				Arguments.of(14, Duration.ofMinutes(26), Duration.ofDays(1), 0));
	}

	@ParameterizedTest(name = "last stage {0} sent at T+{1}, evaluated at T+{2} -> stage {3}")
	@MethodSource("repeatTable")
	void highTasksRepeatByTheIntervalUpToTheCap(int lastStage, Duration sentAfter, Duration evaluatedAfter,
			int expected) {
		Task task = taskReminded(Urgency.HIGH, lastStage, DEADLINE.plus(sentAfter));

		var decision = policy.decide(task, DEADLINE.plus(evaluatedAfter));

		if (expected == 0) {
			assertThat(decision).isEmpty();
		} else {
			assertThat(decision).map(ReminderDecision::stage).contains(expected);
		}
	}

	@Test
	void overdueDecisionsCarryTheirRepeatNumberAndTheFinalOneTheCap() {
		Instant sent = DEADLINE.plus(Duration.ofMinutes(10));
		Instant due = sent.plus(INTERVAL);

		assertThat(policy.decide(taskReminded(Urgency.HIGH, 3, sent), due)).contains(ReminderDecision.overdue(4, 1));
		assertThat(policy.decide(taskReminded(Urgency.HIGH, 9, sent), due)).contains(ReminderDecision.overdue(10, 7));
		assertThat(policy.decide(taskReminded(Urgency.HIGH, 13, sent), due))
				.contains(ReminderDecision.finalStop(14, 10));
	}

	@Test
	void aStageAboveThreeIsNeverDueBeforeTheDeadline() {
		Task stale = taskReminded(Urgency.HIGH, 5, DEADLINE.minus(Duration.ofHours(1)));

		assertThat(policy.decide(stale, DEADLINE.minus(Duration.ofMinutes(1)))).isEmpty();
	}

	@Test
	void aCustomIntervalAndCapAreHonored() {
		ReminderPolicy custom = new ReminderPolicy(Duration.ofMinutes(1), 3);
		Instant sent = DEADLINE;
		Instant oneMinuteLater = sent.plus(Duration.ofMinutes(1));

		assertThat(custom.decide(taskReminded(Urgency.HIGH, 3, sent), oneMinuteLater)).contains(ReminderDecision.overdue(4, 1));
		assertThat(custom.decide(taskReminded(Urgency.HIGH, 6, sent), oneMinuteLater))
				.contains(ReminderDecision.finalStop(7, 3));
		assertThat(custom.decide(taskReminded(Urgency.HIGH, 7, sent), oneMinuteLater)).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("lowAndMedium")
	void lowAndMediumNeverRepeat(Urgency urgency) {
		Task exhausted = taskReminded(urgency, policy.finalStage(urgency), DEADLINE);

		assertThat(policy.decide(exhausted, DEADLINE.plus(Duration.ofDays(1)))).isEmpty();
	}

	static Stream<Urgency> lowAndMedium() {
		return Stream.of(Urgency.LOW, Urgency.MEDIUM);
	}

	@Test
	void aDoneTaskStopsRepeating() {
		Task done = Task.restore(UUID.randomUUID(), 1L, "Report", Urgency.HIGH, TaskStatus.DONE, null, DEADLINE,
				(short) 5, DEADLINE, DEADLINE, DEADLINE);

		assertThat(policy.decide(done, DEADLINE.plus(Duration.ofHours(1)))).isEmpty();
	}

	@Test
	void anIgnoredHighTaskIsNaggedOnceEveryIntervalUpToTheCapThenOnceMore() {
		Task task = taskReminded(Urgency.HIGH, 3, DEADLINE);
		List<Integer> stages = new ArrayList<>();
		for (Instant now = DEADLINE; now.isBefore(DEADLINE.plus(Duration.ofHours(1))); now = now.plusSeconds(30)) {
			Optional<ReminderDecision> decision = policy.decide(task, now);
			if (decision.isPresent()) {
				stages.add(decision.get().stage());
				task = taskReminded(Urgency.HIGH, decision.get().stage(), now);
			}
		}

		assertThat(stages).containsExactly(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
	}

	@Test
	void aSnoozedTaskIsDueForStageOneImmediatelyAndRestartsTheCycle() {
		Instant now = DEADLINE.plus(Duration.ofMinutes(20));
		Task snoozed = Task.restore(UUID.randomUUID(), 1L, "Report", Urgency.HIGH, TaskStatus.PENDING, null,
				now.plus(ReminderPolicy.SNOOZE), (short) 0, DEADLINE.plus(Duration.ofMinutes(18)), DEADLINE, now);

		assertThat(policy.decide(snoozed, now)).contains(ReminderDecision.scheduled(1));
		assertThat(policy.decide(taskSnoozedAfterStage(snoozed, 1), now.plus(Duration.ofMinutes(5))))
				.contains(ReminderDecision.scheduled(2));
	}

	private static Task taskSnoozedAfterStage(Task task, int stage) {
		return Task.restore(task.id(), task.chatId(), task.title(), task.urgency(), task.status(), null,
				task.deadlineAt(), (short) stage, task.updatedAt(), task.createdAt(), task.updatedAt());
	}
}
