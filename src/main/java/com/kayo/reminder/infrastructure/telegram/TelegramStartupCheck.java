package com.kayo.reminder.infrastructure.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Asks Telegram which bot the token belongs to, so a wrong or revoked token is one clear log line instead of endless
 * polling errors. Never throws and never stops the app: an unreachable Telegram at boot is only a warning. Neither the
 * token nor the library's error text (which can contain the request URL) is logged.
 */
class TelegramStartupCheck {

	private static final Logger log = LoggerFactory.getLogger(TelegramStartupCheck.class);

	private final TelegramClient client;

	TelegramStartupCheck(TelegramClient client) {
		this.client = client;
	}

	void run() {
		try {
			User bot = client.execute(new GetMe());
			log.info("Telegram accepted the bot token: running as @{}", bot == null ? "?" : bot.getUserName());
		}
		catch (TelegramApiRequestException ex) {
			Integer code = ex.getErrorCode();
			if (code != null && (code == 401 || code == 404)) {
				log.error("Telegram rejected TELEGRAM_BOT_TOKEN (error {}): the token is invalid or was revoked, so the "
						+ "bot cannot receive messages. Fix the token and restart.", code);
			}
			else {
				log.warn("Could not verify TELEGRAM_BOT_TOKEN: Telegram answered with error {}", code);
			}
		}
		catch (TelegramApiException | RuntimeException ex) {
			log.warn("Could not verify TELEGRAM_BOT_TOKEN: Telegram is not reachable ({})",
					ex.getCause() != null ? ex.getCause().getClass().getSimpleName() : ex.getClass().getSimpleName());
		}
	}
}
