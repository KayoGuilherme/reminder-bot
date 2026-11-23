package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.Messages;
import com.kayo.reminder.application.component.TaskSummaryFormatter;
import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ListTasksService {

	public static final int LIMIT = 20;

	private static final Logger log = LoggerFactory.getLogger(ListTasksService.class);

	private final TaskService tasks;
	private final MessageSender sender;
	private final Messages messages;
	private final TaskSummaryFormatter formatter;
	private final Clock clock;

	public ListTasksService(TaskService tasks, MessageSender sender, Messages messages,
			AppProperties properties, Clock clock) {
		this.tasks = tasks;
		this.sender = sender;
		this.messages = messages;
		this.formatter = new TaskSummaryFormatter(messages, properties.timezone());
		this.clock = clock;
	}

	public void list(long chatId) {
		List<TaskResponse> pending = tasks.findPendingByChat(chatId, LIMIT);
		Optional.of(pending).filter(found -> !found.isEmpty()).ifPresentOrElse(found -> show(chatId, found),
				() -> sender.sendText(chatId, messages.get("tasks.empty")));
	}

	private void show(long chatId, List<TaskResponse> pending) {
		Instant now = clock.instant();
		pending.forEach(task -> sender.sendWithButtons(chatId, formatter.listItem(task, now), List.of(List.of(
				new ButtonResponse(messages.get("tasks.done-button"), ReminderActionService.DONE_PREFIX + task.id()),
				new ButtonResponse(messages.get("tasks.cancel-button"), ReminderActionService.CANCEL_PREFIX + task.id())))));
		announceRemaining(chatId, pending.size());
		log.info("Listed {} pending tasks for chat {}", pending.size(), chatId);
	}

	private void announceRemaining(long chatId, int shown) {
		Optional.of(shown).filter(count -> count == LIMIT).map(count -> tasks.countPendingByChat(chatId) - LIMIT)
				.filter(more -> more > 0)
				.ifPresent(more -> sender.sendText(chatId, messages.get("tasks.more", more)));
	}
}
