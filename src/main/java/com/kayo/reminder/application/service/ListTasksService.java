package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.HandleReminderActionUseCase;
import com.kayo.reminder.application.port.in.ListTasksUseCase;
import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.Task;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * One message per task, in deadline order, so each message's Done and Cancel buttons can be edited into a
 * confirmation. A failed send is reported by the sender and does not stop the rest. Only ids and counts are logged.
 */
@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ListTasksService implements ListTasksUseCase {

	private static final Logger log = LoggerFactory.getLogger(ListTasksService.class);

	private final TaskRepository tasks;
	private final MessageSenderPort sender;
	private final Messages messages;
	private final TaskSummaryFormatter formatter;
	private final Clock clock;

	public ListTasksService(TaskRepository tasks, MessageSenderPort sender, Messages messages,
			AppProperties properties, Clock clock) {
		this.tasks = tasks;
		this.sender = sender;
		this.messages = messages;
		this.formatter = new TaskSummaryFormatter(messages, properties.timezone());
		this.clock = clock;
	}

	@Override
	public void list(long chatId) {
		List<Task> pending = tasks.findPendingByChat(chatId, LIMIT);
		if (pending.isEmpty()) {
			sender.sendText(chatId, messages.get("tasks.empty"));
			return;
		}
		Instant now = clock.instant();
		for (Task task : pending) {
			sender.sendWithButtons(chatId, formatter.listItem(task, now), List.of(List.of(
					new Button(messages.get("tasks.done-button"), HandleReminderActionUseCase.DONE_PREFIX + task.id()),
					new Button(messages.get("tasks.cancel-button"),
							HandleReminderActionUseCase.CANCEL_PREFIX + task.id()))));
		}
		if (pending.size() == LIMIT) {
			long more = tasks.countPendingByChat(chatId) - LIMIT;
			if (more > 0) {
				sender.sendText(chatId, messages.get("tasks.more", more));
			}
		}
		log.info("Listed {} pending tasks for chat {}", pending.size(), chatId);
	}
}
