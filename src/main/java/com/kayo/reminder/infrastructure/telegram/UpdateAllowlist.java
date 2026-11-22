package com.kayo.reminder.infrastructure.telegram;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;

/** Lets through only updates sent by the one allowed user. Logs rejected sender ids, never their content. */
class UpdateAllowlist {

	private static final Logger log = LoggerFactory.getLogger(UpdateAllowlist.class);

	private final long allowedUserId;

	UpdateAllowlist(long allowedUserId) {
		this.allowedUserId = allowedUserId;
	}

	boolean isAllowed(Update update) {
		Optional<Long> sender = senderId(update);
		if (sender.isEmpty()) {
			log.debug("Ignoring update without a sender");
			return false;
		}
		if (sender.get() != allowedUserId) {
			log.warn("Ignoring update from unauthorized user id={}", sender.get());
			return false;
		}
		return true;
	}

	private static Optional<Long> senderId(Update update) {
		if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
			return Optional.ofNullable(update.getCallbackQuery().getFrom().getId());
		}
		if (update.hasMessage() && update.getMessage().getFrom() != null) {
			return Optional.ofNullable(update.getMessage().getFrom().getId());
		}
		return Optional.empty();
	}
}
