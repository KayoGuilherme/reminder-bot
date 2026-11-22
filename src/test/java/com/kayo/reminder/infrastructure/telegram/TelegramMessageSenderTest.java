package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.SendResult.FailureReason;
import com.kayo.reminder.config.AppProperties.Telegram.Retry;
import com.kayo.reminder.testsupport.LogCapture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.ApiResponse;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Exercises the sender against a fake Telegram HTTP API; delays are recorded, never slept. */
class TelegramMessageSenderTest {

	private static final Retry RETRY = new Retry(3, Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(30));

	private FakeTelegramApi api;
	private final List<Duration> sleeps = new ArrayList<>();

	@BeforeEach
	void startApi() throws Exception {
		api = new FakeTelegramApi();
	}

	@AfterEach
	void stopApi() {
		api.close();
		Thread.interrupted(); // clear a flag a test may have left set
	}

	private TelegramMessageSender sender(Retry retry) {
		return new TelegramMessageSender(api.client(), retry, FakeTelegramApi.TOKEN, sleeps::add);
	}

	@Test
	void sendTextReturnsMessageId() {
		api.sentMessage(77);
		SendResult result = sender(RETRY).sendText(5, "hello there");
		assertThat(result.success()).isTrue();
		assertThat(result.messageId()).isEqualTo(77);
		assertThat(api.requests()).hasSize(1);
		assertThat(api.requests().get(0).path()).endsWith("/sendmessage");
		assertThat(api.requests().get(0).body()).contains("\"chat_id\":\"5\"").contains("hello there");
		assertThat(sleeps).isEmpty();
	}

	@Test
	void sendWithButtonsDeliversInlineKeyboard() {
		api.sentMessage(78);
		SendResult result = sender(RETRY).sendWithButtons(5, "pick one", List.of(
				List.of(new Button("Yes", "ans:yes"), new Button("No", "ans:no")),
				List.of(new Button("Later", "ans:later"))));
		assertThat(result.success()).isTrue();
		assertThat(result.messageId()).isEqualTo(78);
		String body = api.requests().get(0).body();
		assertThat(body).contains("inline_keyboard").contains("ans:yes").contains("ans:no").contains("ans:later")
				.contains("\"text\":\"Later\"");
		assertThat(body.indexOf("ans:later")).isGreaterThan(body.indexOf("ans:no"));
		assertThat(body).contains("],[");
	}

	@Test
	void editTextSucceeds() {
		api.sentMessage(55);
		SendResult result = sender(RETRY).editText(5, 55, "new text");
		assertThat(result.success()).isTrue();
		assertThat(api.requests().get(0).path()).endsWith("/editmessagetext");
		assertThat(api.requests().get(0).body()).contains("\"message_id\":55").contains("new text");
	}

	@Test
	void editToIdenticalTextIsSuccess() {
		api.error(400, "Bad Request: message is not modified: specified new message content and reply markup are "
				+ "exactly the same as a current content and reply markup of the message");
		SendResult result = sender(RETRY).editText(5, 55, "same");
		assertThat(result.success()).isTrue();
		assertThat(api.requests()).hasSize(1);
	}

	@Test
	void notModifiedOnSendIsStillRejected() {
		api.error(400, "Bad Request: message is not modified");
		SendResult result = sender(RETRY).sendText(5, "x");
		assertThat(result.failure()).isEqualTo(FailureReason.REJECTED);
	}

	@Test
	void answerCallbackSucceeds() {
		api.ok();
		SendResult result = sender(RETRY).answerCallback("cb-1");
		assertThat(result.success()).isTrue();
		assertThat(api.requests().get(0).path()).endsWith("/answercallbackquery");
		assertThat(api.requests().get(0).body()).contains("cb-1");
	}

	@Test
	void rateLimitWithShortRetryAfterWaitsThenSucceeds() {
		api.rateLimited(2).sentMessage(90);
		SendResult result = sender(RETRY).sendText(5, "hi");
		assertThat(result.success()).isTrue();
		assertThat(result.messageId()).isEqualTo(90);
		assertThat(api.requests()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
	}

	@Test
	void rateLimitAboveMaximumFailsWithoutWaiting() {
		api.rateLimited(100);
		SendResult result = sender(RETRY).sendText(5, "hi");
		assertThat(result.success()).isFalse();
		assertThat(result.failure()).isEqualTo(FailureReason.RATE_LIMITED);
		assertThat(api.requests()).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void persistentRateLimitStopsAtMaxAttempts() {
		api.replyAlways(429, "{\"ok\":false,\"error_code\":429,\"description\":\"Too Many Requests\","
				+ "\"parameters\":{\"retry_after\":1}}");
		SendResult result = sender(RETRY).sendText(5, "hi");
		assertThat(result.failure()).isEqualTo(FailureReason.RATE_LIMITED);
		assertThat(api.requests()).hasSize(3);
		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

	@Test
	void transientFailureThenSuccessMakesTwoAttemptsWithOnePause() {
		api.error(502, "Bad Gateway").sentMessage(91);
		SendResult result = sender(RETRY).sendText(5, "hi");
		assertThat(result.success()).isTrue();
		assertThat(api.requests()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void gatewayHtmlPageIsTreatedAsTransient() {
		api.reply(502, "<html>bad gateway</html>").sentMessage(92);
		SendResult result = sender(RETRY).sendText(5, "hi");
		assertThat(result.success()).isTrue();
		assertThat(api.requests()).hasSize(2);
	}

	@Test
	void persistentTransientFailureStopsAfterMaxAttemptsWithGrowingBackoff() {
		api.errorAlways(502, "Bad Gateway");
		SendResult result = sender(new Retry(4, Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(30)))
				.sendText(5, "hi");
		assertThat(result.success()).isFalse();
		assertThat(result.failure()).isEqualTo(FailureReason.TRANSIENT);
		assertThat(api.requests()).hasSize(4);
		// 500ms, then doubled to 1s, then capped at maxBackoff (1s)
		assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(1));
	}

	@Test
	void defaultPolicyBackoffGrowsBetweenAttempts() {
		api.errorAlways(500, "Internal Server Error");
		sender(RETRY).sendText(5, "hi");
		assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(1));
	}

	@Test
	void forbiddenIsRejectedAfterOneAttempt() {
		api.error(403, "Forbidden: bot was blocked by the user");
		SendResult result = sender(RETRY).sendText(5, "hi");
		assertThat(result.failure()).isEqualTo(FailureReason.REJECTED);
		assertThat(api.requests()).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void otherClientErrorsAreNotRetried() {
		for (int code : new int[] {400, 401, 404}) {
			api.error(code, "client error " + code);
		}
		TelegramMessageSender sender = sender(RETRY);
		assertThat(sender.sendText(5, "a").failure()).isEqualTo(FailureReason.REJECTED);
		assertThat(sender.sendText(5, "b").failure()).isEqualTo(FailureReason.REJECTED);
		assertThat(sender.sendText(5, "c").failure()).isEqualTo(FailureReason.REJECTED);
		assertThat(api.requests()).hasSize(3);
	}

	@Test
	void connectionFailureIsTransientAndDoesNotThrow() {
		TelegramMessageSender sender = sender(RETRY);
		api.stop();
		SendResult result = sender.sendText(5, "hi");
		assertThat(result.success()).isFalse();
		assertThat(result.failure()).isEqualTo(FailureReason.TRANSIENT);
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void interruptedWaitReturnsUnexpectedAndKeepsInterruptFlag() {
		api.error(502, "Bad Gateway").sentMessage(1);
		TelegramMessageSender sender = new TelegramMessageSender(api.client(), RETRY, FakeTelegramApi.TOKEN, d -> {
			throw new InterruptedException("stop");
		});
		SendResult result = sender.sendText(5, "hi");
		assertThat(result.failure()).isEqualTo(FailureReason.UNEXPECTED);
		assertThat(Thread.currentThread().isInterrupted()).isTrue();
	}

	@Test
	void unexpectedClientExceptionBecomesFailedResult() throws Exception {
		TelegramClient client = mock(TelegramClient.class);
		when(client.execute(any(SendMessage.class))).thenThrow(new IllegalStateException("kaboom"));
		SendResult result = new TelegramMessageSender(client, RETRY, FakeTelegramApi.TOKEN, sleeps::add)
				.sendText(5, "hi");
		assertThat(result.failure()).isEqualTo(FailureReason.UNEXPECTED);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void failureLogsNeitherMessageTextNorToken() {
		api.error(403, "Forbidden: bot was blocked by the user");
		try (LogCapture logs = new LogCapture(TelegramMessageSender.class)) {
			TelegramMessageSender sender = sender(RETRY);
			sender.sendText(5, "my very private reminder");
			api.stop();
			sender.sendText(5, "another private reminder");
			assertThat(logs.events(Level.WARN)).hasSize(2);
			assertThat(logs.allText()).contains("sendText").contains("5").contains("REJECTED").contains("TRANSIENT")
					.doesNotContain("private").doesNotContain(FakeTelegramApi.TOKEN);
		}
	}

	@Test
	void tokenInTelegramsErrorDescriptionIsRedacted() throws Exception {
		ApiResponse<Boolean> response = new ApiResponse<>(false, 403, "Forbidden at /bot" + FakeTelegramApi.TOKEN, null, null);
		TelegramClient client = mock(TelegramClient.class);
		when(client.execute(any(SendMessage.class)))
				.thenThrow(new TelegramApiRequestException("failed", response));
		try (LogCapture logs = new LogCapture(TelegramMessageSender.class)) {
			SendResult result = new TelegramMessageSender(client, RETRY, FakeTelegramApi.TOKEN, sleeps::add)
					.sendText(5, "hi");
			assertThat(result.failure()).isEqualTo(FailureReason.REJECTED);
			assertThat(logs.allText()).contains("Forbidden at /bot***").doesNotContain(FakeTelegramApi.TOKEN);
		}
	}
}
