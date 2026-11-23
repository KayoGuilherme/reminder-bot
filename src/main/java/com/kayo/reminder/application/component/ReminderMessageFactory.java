package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.ReminderDecisionResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.service.ReminderActionService;
import com.kayo.reminder.domain.models.enums.ReminderKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReminderMessageFactory {

	@FunctionalInterface
	private interface MessageBuilder {

		String build(TaskResponse task, ReminderDecisionResponse decision, Duration untilDeadline);
	}

	private static final int OVERDUE_BANDS = 3;
	private static final int REPEATS_PER_BAND = 3;

	private final Messages messages;
	private final Map<ReminderKind, MessageBuilder> builders = Map.of(
			ReminderKind.SCHEDULED, this::scheduled,
			ReminderKind.OVERDUE, this::overdue,
			ReminderKind.FINAL, this::finalStop);

	public ReminderMessageFactory(Messages messages) {
		this.messages = messages;
	}

	public String create(TaskResponse task, ReminderDecisionResponse decision, Instant now) {
		return builders.get(decision.kind()).build(task, decision, Duration.between(now, task.deadlineAt()));
	}

	public List<List<ButtonResponse>> buttons(TaskResponse task) {
		return List.of(List.of(
				new ButtonResponse(messages.get("reminder.done-button"), ReminderActionService.DONE_PREFIX + task.id()),
				new ButtonResponse(messages.get("reminder.snooze-button"),
						ReminderActionService.SNOOZE_PREFIX + task.id())));
	}

	private String scheduled(TaskResponse task, ReminderDecisionResponse decision, Duration untilDeadline) {
		long minutes = Math.max(0, Math.ceilDiv(untilDeadline.toMillis(), 60_000L));
		return messages.get("reminder." + task.urgency() + "." + decision.stage(), task.title(), minutes);
	}

	private String overdue(TaskResponse task, ReminderDecisionResponse decision, Duration untilDeadline) {
		return messages.get("reminder.HIGH.overdue." + band(decision.repeat()), task.title(),
				minutesOverdue(untilDeadline));
	}

	private String finalStop(TaskResponse task, ReminderDecisionResponse decision, Duration untilDeadline) {
		return messages.get("reminder.HIGH.final", task.title(), minutesOverdue(untilDeadline));
	}

	private static int band(int repeat) {
		return Math.min(OVERDUE_BANDS, (Math.max(repeat, 1) - 1) / REPEATS_PER_BAND + 1);
	}

	private static long minutesOverdue(Duration untilDeadline) {
		return Math.max(0, untilDeadline.negated().toMinutes());
	}
}
