package com.kayo.reminder.transport.telegram;

import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.config.RetryProperties;
import com.kayo.reminder.domain.models.enums.SendFailureReason;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.ResponseParameters;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class TelegramMessageSender implements MessageSender {

	@FunctionalInterface
	interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

	private static final Logger log = LoggerFactory.getLogger(TelegramMessageSender.class);
	private static final int MAX_LOGGED_DETAIL = 200;

	private final TelegramClient client;
	private final RetryProperties retry;
	private final String botToken;
	private final Sleeper sleeper;

	public TelegramMessageSender(TelegramClient client, RetryProperties retry, String botToken, Sleeper sleeper) {
		this.client = client;
		this.retry = retry;
		this.botToken = botToken;
		this.sleeper = sleeper;
	}

	public TelegramMessageSender(TelegramClient client, RetryProperties retry, String botToken) {
		this(client, retry, botToken, duration -> Thread.sleep(duration));
	}

	@Override
	public SendResultResponse sendText(long chatId, String text) {
		return run("sendText", chatId, false, () -> {
			Message sent = client.execute(SendMessage.builder().chatId(chatId).text(text).build());
			return sent.getMessageId();
		});
	}

	@Override
	public SendResultResponse sendWithButtons(long chatId, String text, List<List<ButtonResponse>> buttons) {
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
	public SendResultResponse editText(long chatId, int messageId, String text) {
		return run("editText", chatId, true, () -> {
			client.execute(EditMessageText.builder().chatId(chatId).messageId(messageId).text(text).build());
			return messageId;
		});
	}

	@Override
	public SendResultResponse answerCallback(String callbackId) {
		return run("answerCallback", null, false, () -> {
			client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
			return null;
		});
	}

	@FunctionalInterface
	private interface Call {
		Integer call() throws TelegramApiException;
	}

	@Value
	private static class Operation {

		String name;
		Long chatId;
		boolean notModifiedIsSuccess;
		Call call;
	}

	private SendResultResponse run(String name, Long chatId, boolean notModifiedIsSuccess, Call call) {
		Operation operation = new Operation(name, chatId, notModifiedIsSuccess, call);
		try {
			return attempt(operation, 1);
		}
		catch (Exception failure) {
			return fail(operation, SendFailureReason.UNEXPECTED, failure.getClass().getSimpleName());
		}
	}

	private SendResultResponse attempt(Operation operation, int attempt) {
		try {
			return SendResultResponse.ok(operation.call().call());
		}
		catch (TelegramApiException error) {
			return handleFailure(operation, attempt, error);
		}
		catch (RuntimeException failure) {
			return fail(operation, SendFailureReason.UNEXPECTED, failure.getClass().getSimpleName());
		}
	}

	private SendResultResponse handleFailure(Operation operation, int attempt, TelegramApiException error) {
		return Optional.of(error).filter(failure -> operation.notModifiedIsSuccess() && isNotModified(failure))
				.map(failure -> SendResultResponse.ok(null)).orElseGet(() -> retryOrFail(operation, attempt, error));
	}

	private SendResultResponse retryOrFail(Operation operation, int attempt, TelegramApiException error) {
		SendFailureReason reason = classify(error);
		return waitBeforeRetry(reason, attempt, error)
				.map(wait -> Optional.of(wait).filter(this::slept).map(slept -> attempt(operation, attempt + 1))
						.orElseGet(() -> fail(operation, SendFailureReason.UNEXPECTED, "interrupted while waiting to retry")))
				.orElseGet(() -> fail(operation, reason, describe(error)));
	}

	private Optional<Duration> waitBeforeRetry(SendFailureReason reason, int attempt, TelegramApiException error) {
		return Optional.of(reason).filter(cause -> cause == SendFailureReason.TRANSIENT || cause == SendFailureReason.RATE_LIMITED)
				.filter(cause -> attempt < retry.maxAttempts())
				.map(cause -> cause == SendFailureReason.RATE_LIMITED ? retryAfter(error) : backoff(attempt))
				.filter(wait -> reason != SendFailureReason.RATE_LIMITED || wait.compareTo(retry.maxRetryAfter()) <= 0);
	}

	private boolean slept(Duration wait) {
		try {
			sleeper.sleep(wait);
			return true;
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static SendFailureReason classify(TelegramApiException error) {
		return Optional.of(error).filter(TelegramApiRequestException.class::isInstance)
				.map(TelegramApiRequestException.class::cast).map(TelegramMessageSender::classifyRequest)
				.orElseGet(() -> hasIoCause(error) ? SendFailureReason.TRANSIENT : SendFailureReason.UNEXPECTED);
	}

	private static SendFailureReason classifyRequest(TelegramApiRequestException request) {
		int code = Optional.ofNullable(request.getErrorCode()).orElse(0);
		return Optional.of(code).filter(value -> value == 429).map(value -> SendFailureReason.RATE_LIMITED)
				.or(() -> Optional.of(code).filter(value -> value >= 500).map(value -> SendFailureReason.TRANSIENT))
				.or(() -> Optional.of(code).filter(value -> value >= 400).map(value -> SendFailureReason.REJECTED))
				.orElse(request.getCause() != null ? SendFailureReason.TRANSIENT : SendFailureReason.UNEXPECTED);
	}

	private static boolean hasIoCause(TelegramApiException error) {
		return Stream.iterate(error.getCause(), Objects::nonNull, Throwable::getCause)
				.anyMatch(IOException.class::isInstance);
	}

	private static boolean isNotModified(TelegramApiException error) {
		return error instanceof TelegramApiRequestException request
				&& request.getErrorCode() != null && request.getErrorCode() == 400
				&& request.getApiResponse() != null
				&& request.getApiResponse().toLowerCase(Locale.ROOT).contains("message is not modified");
	}

	private static Duration retryAfter(TelegramApiException error) {
		return Optional.of(error).filter(TelegramApiRequestException.class::isInstance)
				.map(TelegramApiRequestException.class::cast).map(TelegramApiRequestException::getParameters)
				.map(ResponseParameters::getRetryAfter).filter(seconds -> seconds > 0).map(Duration::ofSeconds)
				.orElse(Duration.ofSeconds(1));
	}

	private Duration backoff(int attempt) {
		Duration delay = retry.initialBackoff();
		for (int i = 1; i < attempt && delay.compareTo(retry.maxBackoff()) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return delay.compareTo(retry.maxBackoff()) > 0 ? retry.maxBackoff() : delay;
	}

	private String describe(TelegramApiException error) {
		return Optional.of(error).filter(TelegramApiRequestException.class::isInstance)
				.map(TelegramApiRequestException.class::cast).filter(request -> request.getApiResponse() != null)
				.map(request -> "code " + request.getErrorCode() + ": " + request.getApiResponse())
				.orElseGet(() -> error.getClass().getSimpleName() + Optional.ofNullable(error.getCause())
						.map(cause -> " caused by " + cause.getClass().getSimpleName()).orElse(""));
	}

	private SendResultResponse fail(Operation operation, SendFailureReason reason, String detail) {
		String safe = redact(detail);
		String logged = safe.length() > MAX_LOGGED_DETAIL ? safe.substring(0, MAX_LOGGED_DETAIL) : safe;
		log.warn("Telegram {} failed for chat {}: {} ({})", operation.name(),
				Optional.ofNullable(operation.chatId()).map(String::valueOf).orElse("-"), reason, logged);
		return SendResultResponse.failed(reason);
	}

	private String redact(String text) {
		return Optional.ofNullable(botToken).filter(token -> !token.isEmpty()).map(token -> text.replace(token, "***"))
				.orElse(text);
	}
}
