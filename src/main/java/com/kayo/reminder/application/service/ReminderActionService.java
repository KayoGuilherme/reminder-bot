package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.MessageReplacer;
import com.kayo.reminder.application.component.Messages;
import com.kayo.reminder.application.component.TaskSummaryFormatter;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderActionService {

	public static final String DONE_PREFIX = "task:done:";
	public static final String SNOOZE_PREFIX = "task:snooze:";
	public static final String CANCEL_PREFIX = "task:cancel:";

	private static final String NOT_AVAILABLE = "reminder.action.not-available";
	private static final String NOT_ACTIVE = "reminder.action.not-active";
	private static final String ALREADY_SNOOZED = "reminder.action.already-snoozed";

	private static final Logger log = LoggerFactory.getLogger(ReminderActionService.class);

	private final TaskService tasks;
	private final MessageSender sender;
	private final Messages messages;
	private final TaskSummaryFormatter formatter;
	private final Clock clock;

	public ReminderActionService(TaskService tasks, MessageSender sender, Messages messages,
			AppProperties properties, Clock clock) {
		this.tasks = tasks;
		this.sender = sender;
		this.messages = messages;
		this.formatter = new TaskSummaryFormatter(messages, properties.timezone());
		this.clock = clock;
	}

	public void done(long chatId, Integer messageId, UUID taskId) {
		owned(chatId, taskId).ifPresentOrElse(
				task -> finish(chatId, messageId, task, ReminderActionService::doneStaleKey,
						() -> tasks.markDone(taskId, clock.instant()), "marked done", "reminder.done.confirmed"),
				() -> reply(chatId, NOT_AVAILABLE));
	}

	public void cancel(long chatId, Integer messageId, UUID taskId) {
		owned(chatId, taskId).ifPresentOrElse(
				task -> finish(chatId, messageId, task, ReminderActionService::cancelStaleKey,
						() -> tasks.markCanceled(taskId, clock.instant()), "canceled", "tasks.canceled.confirmed"),
				() -> reply(chatId, NOT_AVAILABLE));
	}

	public void snooze(long chatId, Integer messageId, UUID taskId) {
		owned(chatId, taskId).ifPresentOrElse(task -> snoozeOwned(chatId, messageId, task),
				() -> reply(chatId, NOT_AVAILABLE));
	}

	private void snoozeOwned(long chatId, Integer messageId, TaskResponse task) {
		Instant now = clock.instant();
		Instant newDeadline = Stream.of(task.deadlineAt(), now).max(Comparator.naturalOrder()).orElseThrow()
				.plus(ReminderPolicy.SNOOZE);
		snoozeFailure(task, newDeadline, now).ifPresentOrElse(key -> reply(chatId, key), () -> {
			log.info("Task {} snoozed", task.id());
			show(chatId, messageId, messages.get("reminder.snoozed", task.title(), formatter.format(newDeadline)));
		});
	}

	private Optional<String> snoozeFailure(TaskResponse task, Instant newDeadline, Instant now) {
		return Optional.of(task).filter(candidate -> candidate.status() != TaskStatus.PENDING)
				.map(candidate -> NOT_ACTIVE)
				.or(() -> Optional.of(task).filter(candidate -> candidate.lastReminderStage() == 0)
						.map(candidate -> ALREADY_SNOOZED))
				.or(() -> Optional.of(task)
						.filter(candidate -> !tasks.snooze(candidate.id(), candidate.deadlineAt(), newDeadline, now))
						.map(candidate -> snoozeRaceKey(candidate.id())));
	}

	private String snoozeRaceKey(UUID taskId) {
		return tasks.findById(taskId).map(current -> current.status() == TaskStatus.PENDING ? ALREADY_SNOOZED : NOT_ACTIVE)
				.orElse(NOT_AVAILABLE);
	}

	private void finish(long chatId, Integer messageId, TaskResponse task, Function<TaskStatus, String> staleKey,
			BooleanSupplier apply, String logText, String confirmedKey) {
		failure(task, staleKey, apply).ifPresentOrElse(key -> reply(chatId, key), () -> {
			log.info("Task {} {}", task.id(), logText);
			show(chatId, messageId, messages.get(confirmedKey, task.title()));
		});
	}

	private Optional<String> failure(TaskResponse task, Function<TaskStatus, String> staleKey, BooleanSupplier apply) {
		return Optional.of(task).filter(candidate -> candidate.status() != TaskStatus.PENDING)
				.map(candidate -> staleKey.apply(candidate.status()))
				.or(() -> Optional.of(task).filter(candidate -> !apply.getAsBoolean())
						.map(candidate -> tasks.findById(candidate.id())
								.map(current -> staleKey.apply(current.status())).orElse(NOT_AVAILABLE)));
	}

	private Optional<TaskResponse> owned(long chatId, UUID taskId) {
		return tasks.findById(taskId).filter(task -> task.chatId() == chatId);
	}

	private static String doneStaleKey(TaskStatus status) {
		return status == TaskStatus.DONE ? "reminder.action.already-done" : NOT_ACTIVE;
	}

	private static String cancelStaleKey(TaskStatus status) {
		return status == TaskStatus.CANCELED ? "tasks.action.already-canceled" : NOT_ACTIVE;
	}

	private void reply(long chatId, String key) {
		sender.sendText(chatId, messages.get(key));
	}

	private void show(long chatId, Integer messageId, String text) {
		MessageReplacer.show(sender, chatId, messageId, text);
	}
}
