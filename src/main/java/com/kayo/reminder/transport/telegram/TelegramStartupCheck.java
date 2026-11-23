package com.kayo.reminder.transport.telegram;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class TelegramStartupCheck {

	private static final Logger log = LoggerFactory.getLogger(TelegramStartupCheck.class);

	private final TelegramClient client;

	public TelegramStartupCheck(TelegramClient client) {
		this.client = client;
	}

	public void run() {
		try {
			User bot = client.execute(new GetMe());
			log.info("Telegram accepted the bot token: running as @{}",
					Optional.ofNullable(bot).map(User::getUserName).orElse("?"));
		}
		catch (TelegramApiRequestException rejected) {
			report(rejected.getErrorCode());
		}
		catch (TelegramApiException | RuntimeException unreachable) {
			log.warn("Could not verify TELEGRAM_BOT_TOKEN: Telegram is not reachable ({})",
					Optional.ofNullable(unreachable.getCause()).orElse(unreachable).getClass().getSimpleName());
		}
	}

	private void report(Integer code) {
		Optional.ofNullable(code).filter(value -> value == 401 || value == 404).ifPresentOrElse(
				value -> log.error("Telegram rejected TELEGRAM_BOT_TOKEN (error {}): the token is invalid or was revoked, "
						+ "so the bot cannot receive messages. Fix the token and restart.", value),
				() -> log.warn("Could not verify TELEGRAM_BOT_TOKEN: Telegram answered with error {}", code));
	}
}
