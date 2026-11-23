package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.CallbackRoute;
import com.kayo.reminder.application.component.Messages;
import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.domain.models.enums.CommandKind;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IncomingCommandService {

	private final MessageSender sender;
	private final Messages messages;
	private final Map<CommandKind, Consumer<IncomingCommandRequest>> handlersByKind;
	private final Map<String, Consumer<IncomingCommandRequest>> textCommands;
	private final List<CallbackRoute> callbackRoutes;

	public IncomingCommandService(MessageSender sender, Messages messages, CreateTaskFromVoiceService createTask,
			ReminderActionService reminderActions, ListTasksService listTasks) {
		this.sender = sender;
		this.messages = messages;
		this.handlersByKind = Map.of(
				CommandKind.CALLBACK, this::handleCallback,
				CommandKind.VOICE, createTask::submit,
				CommandKind.TEXT, this::handleText);
		this.textCommands = Map.of(
				"/start", this::sendUsage,
				"/help", this::sendUsage,
				"/tasks", command -> listTasks.list(command.chatId()),
				"", createTask::submit);
		this.callbackRoutes = List.of(
				new CallbackRoute(CreateTaskFromVoiceService.CONFIRM_PREFIX, createTask::confirm),
				new CallbackRoute(CreateTaskFromVoiceService.CANCEL_PREFIX, createTask::cancel),
				new CallbackRoute(ReminderActionService.DONE_PREFIX, reminderActions::done),
				new CallbackRoute(ReminderActionService.CANCEL_PREFIX, reminderActions::cancel),
				new CallbackRoute(ReminderActionService.SNOOZE_PREFIX, reminderActions::snooze));
	}

	public void handle(IncomingCommandRequest command) {
		handlersByKind.get(command.kind()).accept(command);
	}

	private void handleText(IncomingCommandRequest command) {
		textCommands.getOrDefault(slashCommand(command.text()), this::sendNotSupported).accept(command);
	}

	private void handleCallback(IncomingCommandRequest command) {
		sender.answerCallback(command.callbackId());
		Optional.ofNullable(command.callbackData())
				.flatMap(data -> callbackRoutes.stream().filter(route -> route.accepts(data)).findFirst())
				.ifPresent(route -> route.run(command));
	}

	private void sendUsage(IncomingCommandRequest command) {
		sender.sendText(command.chatId(), messages.get("usage"));
	}

	private void sendNotSupported(IncomingCommandRequest command) {
		sender.sendText(command.chatId(), messages.get("not-supported-yet"));
	}

	private static String slashCommand(String text) {
		return Optional.of(text.strip()).filter(trimmed -> trimmed.startsWith("/"))
				.map(IncomingCommandService::commandWithoutBotName).orElse("");
	}

	private static String commandWithoutBotName(String trimmed) {
		String token = trimmed.chars().takeWhile(character -> !Character.isWhitespace(character))
				.mapToObj(character -> String.valueOf((char) character)).collect(Collectors.joining());
		return token.split("@", 2)[0].toLowerCase(Locale.ROOT);
	}
}
