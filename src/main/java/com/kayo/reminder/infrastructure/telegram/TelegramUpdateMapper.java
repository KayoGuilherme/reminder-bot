package com.kayo.reminder.infrastructure.telegram;

import com.kayo.reminder.application.port.in.IncomingCommand;
import java.util.Optional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/** Converts a Telegram update into a Telegram-agnostic command; update kinds we do not handle give empty. */
class TelegramUpdateMapper {

	Optional<IncomingCommand> toCommand(Update update) {
		if (update == null) {
			return Optional.empty();
		}
		if (update.hasCallbackQuery()) {
			return callback(update.getCallbackQuery());
		}
		if (update.hasMessage()) {
			return message(update.getMessage());
		}
		return Optional.empty();
	}

	private Optional<IncomingCommand> message(Message message) {
		if (message.getFrom() == null || message.getFrom().getId() == null || message.getChatId() == null) {
			return Optional.empty();
		}
		long chatId = message.getChatId();
		long userId = message.getFrom().getId();
		if (message.hasText()) {
			return Optional.of(IncomingCommand.text(chatId, userId, message.getMessageId(), message.getText()));
		}
		if (message.hasVoice() && message.getVoice().getFileId() != null) {
			return Optional.of(IncomingCommand.voice(chatId, userId, message.getMessageId(),
					message.getVoice().getFileId(), message.getVoice().getDuration()));
		}
		return Optional.empty();
	}

	private Optional<IncomingCommand> callback(CallbackQuery query) {
		MaybeInaccessibleMessage message = query.getMessage();
		if (query.getId() == null || query.getFrom() == null || query.getFrom().getId() == null
				|| message == null || message.getChatId() == null) {
			return Optional.empty();
		}
		return Optional.of(IncomingCommand.callback(message.getChatId(), query.getFrom().getId(),
				message.getMessageId(), query.getId(), query.getData()));
	}
}
