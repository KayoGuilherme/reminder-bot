package com.kayo.reminder.transport.telegram;

import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

public class TelegramBot implements SpringLongPollingBot {

	private final String botToken;
	private final LongPollingUpdateConsumer consumer;

	public TelegramBot(String botToken, LongPollingUpdateConsumer consumer) {
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
