package com.kayo.reminder.transport.telegram;

import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import java.util.Optional;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

public class TelegramUpdateMapper {

	public Optional<IncomingCommandRequest> toCommand(Update update) {
		return Optional.ofNullable(update).flatMap(this::fromUpdate);
	}

	private Optional<IncomingCommandRequest> fromUpdate(Update update) {
		return Optional.of(update).filter(Update::hasCallbackQuery)
				.flatMap(withCallback -> callback(withCallback.getCallbackQuery()))
				.or(() -> Optional.of(update).filter(Update::hasMessage)
						.flatMap(withMessage -> message(withMessage.getMessage())));
	}

	private Optional<IncomingCommandRequest> message(Message message) {
		return Optional.of(message)
				.filter(sent -> sent.getFrom() != null && sent.getFrom().getId() != null && sent.getChatId() != null)
				.flatMap(sent -> textCommand(sent).or(() -> voiceCommand(sent)));
	}

	private Optional<IncomingCommandRequest> textCommand(Message message) {
		return Optional.of(message).filter(Message::hasText).map(sent -> IncomingCommandRequest.text(sent.getChatId(),
				sent.getFrom().getId(), sent.getMessageId(), sent.getText()));
	}

	private Optional<IncomingCommandRequest> voiceCommand(Message message) {
		return Optional.of(message).filter(Message::hasVoice).filter(sent -> sent.getVoice().getFileId() != null)
				.map(sent -> IncomingCommandRequest.voice(sent.getChatId(), sent.getFrom().getId(), sent.getMessageId(),
						sent.getVoice().getFileId(), sent.getVoice().getDuration()));
	}

	private Optional<IncomingCommandRequest> callback(CallbackQuery query) {
		return Optional.of(query)
				.filter(pressed -> pressed.getId() != null && pressed.getFrom() != null
						&& pressed.getFrom().getId() != null)
				.flatMap(pressed -> Optional.ofNullable(pressed.getMessage())
						.filter(origin -> origin.getChatId() != null)
						.map(origin -> IncomingCommandRequest.callback(origin.getChatId(), pressed.getFrom().getId(),
								origin.getMessageId(), pressed.getId(), pressed.getData())));
	}
}
