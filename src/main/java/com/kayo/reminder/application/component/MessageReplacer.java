package com.kayo.reminder.application.component;

import com.kayo.reminder.transport.telegram.MessageSender;
import java.util.Optional;

public final class MessageReplacer {

	private MessageReplacer() {
	}

	public static void show(MessageSender sender, long chatId, Integer messageId, String text) {
		boolean edited = Optional.ofNullable(messageId).map(id -> sender.editText(chatId, id, text).success())
				.orElse(false);
		Optional.of(edited).filter(done -> !done).ifPresent(notEdited -> sender.sendText(chatId, text));
	}
}
