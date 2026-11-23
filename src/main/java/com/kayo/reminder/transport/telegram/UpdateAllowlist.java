package com.kayo.reminder.transport.telegram;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

public class UpdateAllowlist {

	private static final Logger log = LoggerFactory.getLogger(UpdateAllowlist.class);

	private final long allowedUserId;

	public UpdateAllowlist(long allowedUserId) {
		this.allowedUserId = allowedUserId;
	}

	public boolean isAllowed(Update update) {
		Optional<Long> sender = senderId(update);
		Optional.of(sender).filter(Optional::isEmpty).ifPresent(none -> log.debug("Ignoring update without a sender"));
		sender.filter(id -> id != allowedUserId)
				.ifPresent(id -> log.warn("Ignoring update from unauthorized user id={}", id));
		return sender.filter(id -> id == allowedUserId).isPresent();
	}

	private static Optional<Long> senderId(Update update) {
		return Optional.of(update).filter(Update::hasCallbackQuery).map(Update::getCallbackQuery)
				.map(CallbackQuery::getFrom).map(User::getId)
				.or(() -> Optional.of(update).filter(Update::hasMessage).map(Update::getMessage)
						.map(Message::getFrom).map(User::getId));
	}
}
