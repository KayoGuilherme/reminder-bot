package com.kayo.reminder.application.service;

import com.kayo.reminder.application.dto.response.ReminderDecisionResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ReminderPolicy {

	public static final Duration LOOKAHEAD = Duration.ofMinutes(10);

	public static final Duration SNOOZE = Duration.ofMinutes(10);

	public static final Duration DEFAULT_REPEAT_INTERVAL = Duration.ofMinutes(2);
	public static final int DEFAULT_MAX_REPEATS = 10;

	private static final Map<Urgency, List<Duration>> OFFSETS = new EnumMap<>(Map.of(
			Urgency.LOW, List.of(LOOKAHEAD),
			Urgency.MEDIUM, List.of(LOOKAHEAD, Duration.ZERO),
			Urgency.HIGH, List.of(LOOKAHEAD, Duration.ofMinutes(5), Duration.ZERO)));

	private final Duration repeatInterval;
	private final int maxRepeats;

	public ReminderPolicy(Duration repeatInterval, int maxRepeats) {
		this.repeatInterval = repeatInterval;
		this.maxRepeats = maxRepeats;
	}

	public static ReminderPolicy defaults() {
		return new ReminderPolicy(DEFAULT_REPEAT_INTERVAL, DEFAULT_MAX_REPEATS);
	}

	public int finalStage(Urgency urgency) {
		return OFFSETS.get(urgency).size() + (urgency == Urgency.HIGH ? maxRepeats + 1 : 0);
	}

	public Map<Urgency, Integer> finalStages() {
		return Arrays.stream(Urgency.values()).collect(Collectors.toMap(Function.identity(), this::finalStage));
	}

	public Optional<ReminderDecisionResponse> decide(TaskResponse task, Instant now) {
		return Optional.of(task).filter(pending -> pending.status() == TaskStatus.PENDING)
				.flatMap(pending -> scheduledDecision(pending, now).or(() -> overdueDecision(pending, now)));
	}

	private Optional<ReminderDecisionResponse> scheduledDecision(TaskResponse task, Instant now) {
		List<Duration> offsets = OFFSETS.get(task.urgency());
		return IntStream.rangeClosed(task.lastReminderStage() + 1, offsets.size()).boxed()
				.sorted(Comparator.reverseOrder())
				.filter(stage -> !now.isBefore(task.deadlineAt().minus(offsets.get(stage - 1))))
				.findFirst().map(ReminderDecisionResponse::scheduled);
	}

	private Optional<ReminderDecisionResponse> overdueDecision(TaskResponse task, Instant now) {
		int scheduledStages = OFFSETS.get(task.urgency()).size();
		return Optional.of(task).filter(candidate -> candidate.urgency() == Urgency.HIGH)
				.filter(candidate -> candidate.lastReminderStage() >= scheduledStages
						&& candidate.lastReminderStage() < finalStage(Urgency.HIGH))
				.filter(candidate -> !now.isBefore(candidate.deadlineAt()))
				.filter(candidate -> isRepeatDue(candidate, now))
				.map(candidate -> overdueAfter(candidate.lastReminderStage() + 1, scheduledStages));
	}

	private boolean isRepeatDue(TaskResponse task, Instant now) {
		return Optional.ofNullable(task.lastRemindedAt()).map(last -> !now.isBefore(last.plus(repeatInterval)))
				.orElse(true);
	}

	private ReminderDecisionResponse overdueAfter(int stage, int scheduledStages) {
		return stage == finalStage(Urgency.HIGH) ? ReminderDecisionResponse.finalStop(stage, maxRepeats)
				: ReminderDecisionResponse.overdue(stage, stage - scheduledStages);
	}
}
