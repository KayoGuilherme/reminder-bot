package com.kayo.reminder.infrastructure.telegram;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/** Builds Telegram updates for tests. */
final class Updates {

	private Updates() {
	}

	static User user(long id) {
		return User.builder().id(id).firstName("u" + id).isBot(false).build();
	}

	static Chat chat(long id) {
		return new Chat(id, "private");
	}

	static Message.MessageBuilder<?, ?> message(long chatId, long userId, int messageId) {
		return Message.builder().messageId(messageId).chat(chat(chatId)).from(user(userId));
	}

	static Update withMessage(int updateId, Message message) {
		Update update = new Update();
		update.setUpdateId(updateId);
		update.setMessage(message);
		return update;
	}

	static Update text(int updateId, long userId, long chatId, String text) {
		return withMessage(updateId, message(chatId, userId, updateId * 10).text(text).build());
	}

	static Update voice(int updateId, long userId, long chatId, String fileId, int duration) {
		Voice voice = Voice.builder().fileId(fileId).fileUniqueId("u-" + fileId).duration(duration).build();
		return withMessage(updateId, message(chatId, userId, updateId * 10).voice(voice).build());
	}

	static Update callback(int updateId, long userId, long chatId, int messageId, String callbackId, String data) {
		CallbackQuery query = new CallbackQuery();
		query.setId(callbackId);
		query.setFrom(user(userId));
		query.setData(data);
		query.setMessage(message(chatId, 999, messageId).text("buttons").build());
		Update update = new Update();
		update.setUpdateId(updateId);
		update.setCallbackQuery(query);
		return update;
	}
}
