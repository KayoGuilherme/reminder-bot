package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.CreateTaskFromVoiceUseCase;
import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.port.in.HandleReminderActionUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Routes what the user sent: slash commands are answered here, voice and other text start the task flow, and the
 * Confirm/Cancel buttons of that flow are handed back to it, and the Done/Snooze buttons of reminders go to the
 * reminder actions.
 */
@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IncomingCommandService implements HandleIncomingCommandUseCase {

	private final MessageSenderPort sender;
	private final Messages messages;
	private final CreateTaskFromVoiceUseCase createTask;
	private final HandleReminderActionUseCase reminderActions;

	public IncomingCommandService(MessageSenderPort sender, Messages messages, CreateTaskFromVoiceUseCase createTask,
			HandleReminderActionUseCase reminderActions) {
		this.sender = sender;
		this.messages = messages;
		this.createTask = createTask;
		this.reminderActions = reminderActions;
	}

	@Override
	public void handle(IncomingCommand command) {
		switch (command.kind()) {
			case CALLBACK -> handleCallback(command);
			case VOICE -> createTask.submit(command);
			case TEXT -> handleText(command);
		}
	}

	private void handleText(IncomingCommand command) {
		switch (slashCommand(command.text())) {
			case "/start", "/help" -> sender.sendText(command.chatId(), messages.get("usage"));
			case "" -> createTask.submit(command);
			default -> sender.sendText(command.chatId(), messages.get("not-supported-yet"));
		}
	}

	/** Always acknowledged, so the client stops showing progress; only the task and reminder buttons do anything more. */
	private void handleCallback(IncomingCommand command) {
		sender.answerCallback(command.callbackId());
		String data = command.callbackData();
		if (data == null) {
			return;
		}
		if (data.startsWith(CreateTaskFromVoiceUseCase.CONFIRM_PREFIX)) {
			uuid(data, CreateTaskFromVoiceUseCase.CONFIRM_PREFIX)
					.ifPresent(id -> createTask.confirm(command.chatId(), command.messageId(), id));
		}
		else if (data.startsWith(CreateTaskFromVoiceUseCase.CANCEL_PREFIX)) {
			uuid(data, CreateTaskFromVoiceUseCase.CANCEL_PREFIX)
					.ifPresent(id -> createTask.cancel(command.chatId(), command.messageId(), id));
		}
		else if (data.startsWith(HandleReminderActionUseCase.DONE_PREFIX)) {
			uuid(data, HandleReminderActionUseCase.DONE_PREFIX)
					.ifPresent(id -> reminderActions.done(command.chatId(), command.messageId(), id));
		}
		else if (data.startsWith(HandleReminderActionUseCase.SNOOZE_PREFIX)) {
			uuid(data, HandleReminderActionUseCase.SNOOZE_PREFIX)
					.ifPresent(id -> reminderActions.snooze(command.chatId(), command.messageId(), id));
		}
	}

	private static Optional<UUID> uuid(String data, String prefix) {
		try {
			return Optional.of(UUID.fromString(data.substring(prefix.length())));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/** The leading "/command" without "@botname" or arguments, lower-cased; empty if the text is not a command. */
	private static String slashCommand(String text) {
		String trimmed = text.strip();
		if (!trimmed.startsWith("/")) {
			return "";
		}
		int end = 0;
		while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
			end++;
		}
		String token = trimmed.substring(0, end);
		int at = token.indexOf('@');
		return (at >= 0 ? token.substring(0, at) : token).toLowerCase(Locale.ROOT);
	}
}
