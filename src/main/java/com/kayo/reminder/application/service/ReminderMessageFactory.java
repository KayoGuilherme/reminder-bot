package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.HandleReminderActionUseCase;
import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.domain.ReminderDecision;
import com.kayo.reminder.domain.Task;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Renders the reminder text for a task's urgency and decision in the configured language. */
@Component
public class ReminderMessageFactory {

	private static final int OVERDUE_BANDS = 3;
	private static final int REPEATS_PER_BAND = 3;

	private final Messages messages;

	public ReminderMessageFactory(Messages messages) {
		this.messages = messages;
	}

	/**
	 * Scheduled stages show the minutes left at {@code now} rounded up, never below 0, so a stage sent a few seconds
	 * late still reads as scheduled and an overdue task never shows a negative number. Overdue and final texts show
	 * the whole minutes since the deadline. The title is a message argument, so it is shown literally.
	 */
	public String create(Task task, ReminderDecision decision, Instant now) {
		Duration untilDeadline = Duration.between(now, task.deadlineAt());
		return switch (decision.kind()) {
			case SCHEDULED -> {
				long minutes = Math.max(0, Math.ceilDiv(untilDeadline.toMillis(), 60_000L));
				yield messages.get("reminder." + task.urgency() + "." + decision.stage(), task.title(), minutes);
			}
			case OVERDUE -> messages.get("reminder.HIGH.overdue." + band(decision.repeat()), task.title(),
					minutesOverdue(untilDeadline));
			case FINAL -> messages.get("reminder.HIGH.final", task.title(), minutesOverdue(untilDeadline));
		};
	}

	/** The Done and Snooze buttons every reminder carries; the callback data names the action and the task. */
	public List<List<Button>> buttons(Task task) {
		return List.of(List.of(
				new Button(messages.get("reminder.done-button"), HandleReminderActionUseCase.DONE_PREFIX + task.id()),
				new Button(messages.get("reminder.snooze-button"),
						HandleReminderActionUseCase.SNOOZE_PREFIX + task.id())));
	}

	/** Repeats 1-3 are band 1, 4-6 band 2 and 7 and above band 3, so a raised cap needs no extra texts. */
	private static int band(int repeat) {
		return Math.min(OVERDUE_BANDS, (Math.max(repeat, 1) - 1) / REPEATS_PER_BAND + 1);
	}

	private static long minutesOverdue(Duration untilDeadline) {
		return Math.max(0, untilDeadline.negated().toMinutes());
	}
}
