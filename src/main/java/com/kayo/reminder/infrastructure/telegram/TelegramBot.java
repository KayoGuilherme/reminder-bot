package com.kayo.reminder.infrastructure.telegram;

import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

/** Registered with Telegram by the long-polling starter. */
class TelegramBot implements SpringLongPollingBot {

	private final String botToken;
	private final LongPollingUpdateConsumer consumer;

	TelegramBot(String botToken, LongPollingUpdateConsumer consumer) {
		this.botToken = botToken;
		this.consumer = consumer;
	}

	@Override
	public String getBotToken() {
		return botToken;
	}

	@Override
	public LongPollingUpdateConsumer getUpdatesConsumer() {
		return consumer;
	}
}
