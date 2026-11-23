package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.HandleReminderActionUseCase;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.ReminderPolicy;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Done, Snooze and Cancel are conditional updates, so pressing a button twice, or a stale one, changes nothing and gets a
 * polite reply. A task of another chat is answered exactly like a missing one. Only ids are logged, never titles.
 */
@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderActionService implements HandleReminderActionUseCase {

	private static final Logger log = LoggerFactory.getLogger(ReminderActionService.class);

	private final TaskRepository tasks;
	private final MessageSenderPort sender;
	private final Messages messages;
	private final TaskSummaryFormatter formatter;
	private final Clock clock;

	public ReminderActionService(TaskRepository tasks, MessageSenderPort sender, Messages messages,
			AppProperties properties, Clock clock) {
		this.tasks = tasks;
		this.sender = sender;
		this.messages = messages;
		this.formatter = new TaskSummaryFormatter(messages, properties.timezone());
		this.clock = clock;
	}

	@Override
	public void done(long chatId, Integer messageId, UUID taskId) {
		Task task = owned(chatId, taskId);
		if (task == null) {
			reply(chatId, "reminder.action.not-available");
			return;
		}
		if (task.status() != TaskStatus.PENDING) {
			reply(chatId, staleReply(task.status()));
			return;
		}
		if (!tasks.markDone(taskId, clock.instant())) {
			reply(chatId, tasks.findById(taskId).map(t -> staleReply(t.status()))
					.orElse("reminder.action.not-available"));
			return;
		}
		log.info("Task {} marked done", taskId);
		show(chatId, messageId, messages.get("reminder.done.confirmed", task.title()));
	}

	@Override
	public void snooze(long chatId, Integer messageId, UUID taskId) {
		Task task = owned(chatId, taskId);
		if (task == null) {
			reply(chatId, "reminder.action.not-available");
			return;
		}
		if (task.status() != TaskStatus.PENDING) {
			reply(chatId, "reminder.action.not-active");
			return;
		}
		if (task.lastReminderStage() == 0) {
			reply(chatId, "reminder.action.already-snoozed");
			return;
		}
		Instant now = clock.instant();
		Instant base = task.deadlineAt().isAfter(now) ? task.deadlineAt() : now;
		Instant newDeadline = base.plus(ReminderPolicy.SNOOZE);
		if (!tasks.snooze(taskId, task.deadlineAt(), newDeadline, now)) {
			reply(chatId, tasks.findById(taskId)
					.map(t -> t.status() == TaskStatus.PENDING ? "reminder.action.already-snoozed"
							: "reminder.action.not-active")
					.orElse("reminder.action.not-available"));
			return;
		}
		log.info("Task {} snoozed", taskId);
		show(chatId, messageId, messages.get("reminder.snoozed", task.title(), formatter.format(newDeadline)));
	}

	@Override
	public void cancel(long chatId, Integer messageId, UUID taskId) {
		Task task = owned(chatId, taskId);
		if (task == null) {
			reply(chatId, "reminder.action.not-available");
			return;
		}
		if (task.status() != TaskStatus.PENDING) {
			reply(chatId, cancelStaleReply(task.status()));
			return;
		}
		if (!tasks.markCanceled(taskId, clock.instant())) {
			reply(chatId, tasks.findById(taskId).map(t -> cancelStaleReply(t.status()))
					.orElse("reminder.action.not-available"));
			return;
		}
		log.info("Task {} canceled", taskId);
		show(chatId, messageId, messages.get("tasks.canceled.confirmed", task.title()));
	}

	/** The task, only if it exists and belongs to {@code chatId}. */
	private Task owned(long chatId, UUID taskId) {
		return tasks.findById(taskId).filter(task -> task.chatId() == chatId).orElse(null);
	}

	private static String staleReply(TaskStatus status) {
		return status == TaskStatus.DONE ? "reminder.action.already-done" : "reminder.action.not-active";
	}

	private static String cancelStaleReply(TaskStatus status) {
		return status == TaskStatus.CANCELED ? "tasks.action.already-canceled" : "reminder.action.not-active";
	}

	private void reply(long chatId, String key) {
		sender.sendText(chatId, messages.get(key));
	}

	/** Replaces the reminder message (removing its buttons); falls back to a new message if that fails. */
	private void show(long chatId, Integer messageId, String text) {
		if (messageId != null && sender.editText(chatId, messageId, text).success()) {
			return;
		}
		sender.sendText(chatId, text);
	}
}
