package com.kayo.reminder.infrastructure.telegram;

import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.SendResult.FailureReason;
import com.kayo.reminder.config.AppProperties.Telegram.Retry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Sends messages through Telegram. Honors 429 {@code retry_after}, retries transient failures with exponential
 * backoff, and turns every failure into a {@link SendResult}: nothing is ever thrown to the caller.
 *
 * <p>Verified against the library with a local fake API: a 429 surfaces as {@link TelegramApiRequestException} with
 * {@code getErrorCode()==429} and {@code getParameters().getRetryAfter()}; a JSON 5xx keeps its code; a non-JSON
 * gateway page has code 0 with a parse failure as cause; a refused connection is a plain
 * {@link TelegramApiException} caused by an {@link IOException}.
 */
class TelegramMessageSender implements MessageSenderPort {

	/** Pauses between attempts; replaced in tests so they never really wait. */
	@FunctionalInterface
	interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

	private static final Logger log = LoggerFactory.getLogger(TelegramMessageSender.class);
	private static final int MAX_LOGGED_DETAIL = 200;

	private final TelegramClient client;
	private final Retry retry;
	private final String botToken;
	private final Sleeper sleeper;

	TelegramMessageSender(TelegramClient client, Retry retry, String botToken, Sleeper sleeper) {
		this.client = client;
		this.retry = retry;
		this.botToken = botToken;
		this.sleeper = sleeper;
	}

	TelegramMessageSender(TelegramClient client, Retry retry, String botToken) {
		this(client, retry, botToken, duration -> Thread.sleep(duration));
	}

	@Override
	public SendResult sendText(long chatId, String text) {
		return run("sendText", chatId, false, () -> {
			Message sent = client.execute(SendMessage.builder().chatId(chatId).text(text).build());
			return sent.getMessageId();
		});
	}

	@Override
	public SendResult sendWithButtons(long chatId, String text, List<List<Button>> buttons) {
		return run("sendWithButtons", chatId, false, () -> {
			List<InlineKeyboardRow> rows = buttons.stream()
					.map(row -> new InlineKeyboardRow(row.stream()
							.map(b -> InlineKeyboardButton.builder().text(b.text()).callbackData(b.callbackData()).build())
							.toList()))
					.toList();
			Message sent = client.execute(SendMessage.builder().chatId(chatId).text(text)
					.replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build()).build());
			return sent.getMessageId();
		});
	}

	@Override
	public SendResult editText(long chatId, int messageId, String text) {
		return run("editText", chatId, true, () -> {
			client.execute(EditMessageText.builder().chatId(chatId).messageId(messageId).text(text).build());
			return messageId;
		});
	}

	@Override
	public SendResult answerCallback(String callbackId) {
		return run("answerCallback", null, false, () -> {
			client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
			return null;
		});
	}

	@FunctionalInterface
	private interface Call {
		Integer call() throws TelegramApiException;
	}

	private SendResult run(String operation, Long chatId, boolean notModifiedIsSuccess, Call call) {
		try {
			return attempt(operation, chatId, notModifiedIsSuccess, call);
		}
		catch (Exception ex) {
			return fail(operation, chatId, FailureReason.UNEXPECTED, ex.getClass().getSimpleName());
		}
	}

	private SendResult attempt(String operation, Long chatId, boolean notModifiedIsSuccess, Call call) {
		for (int attempt = 1;; attempt++) {
			TelegramApiException error;
			try {
				return SendResult.ok(call.call());
			}
			catch (TelegramApiException ex) {
				error = ex;
			}
			catch (RuntimeException ex) {
				return fail(operation, chatId, FailureReason.UNEXPECTED, ex.getClass().getSimpleName());
			}

			if (notModifiedIsSuccess && isNotModified(error)) {
				return SendResult.ok(null);
			}
			FailureReason reason = classify(error);
			String detail = describe(error);
			if (reason == FailureReason.REJECTED || reason == FailureReason.UNEXPECTED || attempt >= retry.maxAttempts()) {
				return fail(operation, chatId, reason, detail);
			}
			Duration wait;
			if (reason == FailureReason.RATE_LIMITED) {
				wait = retryAfter(error);
				if (wait.compareTo(retry.maxRetryAfter()) > 0) {
					return fail(operation, chatId, reason, detail);
				}
			}
			else {
				wait = backoff(attempt);
			}
			try {
				sleeper.sleep(wait);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return fail(operation, chatId, FailureReason.UNEXPECTED, "interrupted while waiting to retry");
			}
		}
	}

	private static FailureReason classify(TelegramApiException error) {
		if (error instanceof TelegramApiRequestException request) {
			int code = request.getErrorCode() == null ? 0 : request.getErrorCode();
			if (code == 429) {
				return FailureReason.RATE_LIMITED;
			}
			if (code >= 500) {
				return FailureReason.TRANSIENT;
			}
			if (code >= 400) {
				return FailureReason.REJECTED;
			}
			// No Telegram error code: the answer could not be read (for example an HTML page from a gateway).
			return error.getCause() != null ? FailureReason.TRANSIENT : FailureReason.UNEXPECTED;
		}
		for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				return FailureReason.TRANSIENT;
			}
		}
		return FailureReason.UNEXPECTED;
	}

	private static boolean isNotModified(TelegramApiException error) {
		return error instanceof TelegramApiRequestException request
				&& request.getErrorCode() != null && request.getErrorCode() == 400
				&& request.getApiResponse() != null
				&& request.getApiResponse().toLowerCase(Locale.ROOT).contains("message is not modified");
	}

	private static Duration retryAfter(TelegramApiException error) {
		if (error instanceof TelegramApiRequestException request && request.getParameters() != null
				&& request.getParameters().getRetryAfter() != null && request.getParameters().getRetryAfter() > 0) {
			return Duration.ofSeconds(request.getParameters().getRetryAfter());
		}
		return Duration.ofSeconds(1);
	}

	private Duration backoff(int attempt) {
		Duration delay = retry.initialBackoff();
		for (int i = 1; i < attempt && delay.compareTo(retry.maxBackoff()) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return delay.compareTo(retry.maxBackoff()) > 0 ? retry.maxBackoff() : delay;
	}

	/** Error code and Telegram's description only: never the stack trace, request URL or message text. */
	private String describe(TelegramApiException error) {
		String text;
		if (error instanceof TelegramApiRequestException request && request.getApiResponse() != null) {
			text = "code " + request.getErrorCode() + ": " + request.getApiResponse();
		}
		else {
			text = error.getClass().getSimpleName()
					+ (error.getCause() != null ? " caused by " + error.getCause().getClass().getSimpleName() : "");
		}
		return text;
	}

	private SendResult fail(String operation, Long chatId, FailureReason reason, String detail) {
		String safe = redact(detail);
		if (safe.length() > MAX_LOGGED_DETAIL) {
			safe = safe.substring(0, MAX_LOGGED_DETAIL);
		}
		log.warn("Telegram {} failed for chat {}: {} ({})", operation, chatId == null ? "-" : chatId, reason, safe);
		return SendResult.failed(reason);
	}

	private String redact(String text) {
		return botToken == null || botToken.isEmpty() ? text : text.replace(botToken, "***");
	}
}
