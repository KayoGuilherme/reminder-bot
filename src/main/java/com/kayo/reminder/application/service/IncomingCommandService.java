package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Routes what the user sent. Only /start, /help and "not supported yet" placeholders exist for now. */
@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IncomingCommandService implements HandleIncomingCommandUseCase {

	private final MessageSenderPort sender;
	private final Messages messages;

	public IncomingCommandService(MessageSenderPort sender, Messages messages) {
		this.sender = sender;
		this.messages = messages;
	}

	@Override
	public void handle(IncomingCommand command) {
		switch (command.kind()) {
			case CALLBACK -> sender.answerCallback(command.callbackId());
			case VOICE -> sender.sendText(command.chatId(), messages.get("not-supported-yet"));
			case TEXT -> handleText(command);
		}
	}

	private void handleText(IncomingCommand command) {
		switch (slashCommand(command.text())) {
			case "/start", "/help" -> sender.sendText(command.chatId(), messages.get("usage"));
			default -> sender.sendText(command.chatId(), messages.get("not-supported-yet"));
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
