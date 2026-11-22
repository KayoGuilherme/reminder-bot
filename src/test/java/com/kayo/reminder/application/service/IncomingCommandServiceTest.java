package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.SendResult.FailureReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class IncomingCommandServiceTest {

	private final RecordingSender sender = new RecordingSender();

	private IncomingCommandService service(String languageTag) {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		return new IncomingCommandService(sender, new Messages(source, Locale.forLanguageTag(languageTag)));
	}

	private static String english(String key) {
		return text("en", key);
	}

	private static String portuguese(String key) {
		return text("pt-BR", key);
	}

	private static String text(String tag, String key) {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		return source.getMessage(key, null, Locale.forLanguageTag(tag));
	}

	@Test
	void startRepliesWithUsage() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "/start"));
		assertThat(sender.texts).containsExactly(new Sent(10, english("usage")));
	}

	@Test
	void helpRepliesWithUsage() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "/help"));
		assertThat(sender.texts).containsExactly(new Sent(10, english("usage")));
	}

	@Test
	void commandAddressedToBotNameOrWithArgumentsIsTreatedAsBareCommand() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommand.text(10, 42, 1, "/help@my_bot"));
		service.handle(IncomingCommand.text(10, 42, 2, "/start@my_bot  some args"));
		service.handle(IncomingCommand.text(10, 42, 3, "  /START now"));
		assertThat(sender.texts).containsOnly(new Sent(10, english("usage"))).hasSize(3);
	}

	@Test
	void plainTextGetsPlaceholder() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "remind me tomorrow"));
		assertThat(sender.texts).containsExactly(new Sent(10, english("not-supported-yet")));
	}

	@Test
	void unknownCommandGetsPlaceholder() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "/foo"));
		assertThat(sender.texts).containsExactly(new Sent(10, english("not-supported-yet")));
	}

	@Test
	void textMentioningStartMidSentenceIsNotACommand() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "please /start"));
		assertThat(sender.texts).containsExactly(new Sent(10, english("not-supported-yet")));
	}

	@Test
	void voiceGetsPlaceholder() {
		service("en").handle(IncomingCommand.voice(10, 42, 1, "file-1", 7));
		assertThat(sender.texts).containsExactly(new Sent(10, english("not-supported-yet")));
	}

	@Test
	void callbackIsAnsweredSilently() {
		service("en").handle(IncomingCommand.callback(10, 42, 1, "cb-1", "x:1"));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1");
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void repliesInPortuguese() {
		IncomingCommandService service = service("pt-BR");
		service.handle(IncomingCommand.text(10, 42, 1, "/start"));
		service.handle(IncomingCommand.text(10, 42, 2, "hello"));
		assertThat(sender.texts).containsExactly(
				new Sent(10, portuguese("usage")), new Sent(10, portuguese("not-supported-yet")));
		assertThat(portuguese("usage")).isNotEqualTo(english("usage"));
	}

	@Test
	void failedSendResultDoesNotThrow() {
		sender.result = SendResult.failed(FailureReason.TRANSIENT);
		IncomingCommandService service = service("en");
		service.handle(IncomingCommand.text(10, 42, 1, "/start"));
		service.handle(IncomingCommand.callback(10, 42, 1, "cb-1", "x"));
		assertThat(sender.texts).hasSize(1);
		assertThat(sender.answeredCallbacks).hasSize(1);
	}

	record Sent(long chatId, String text) {
	}

	static class RecordingSender implements MessageSenderPort {

		final List<Sent> texts = new ArrayList<>();
		final List<String> answeredCallbacks = new ArrayList<>();
		SendResult result = SendResult.ok(1);

		@Override
		public SendResult sendText(long chatId, String text) {
			texts.add(new Sent(chatId, text));
			return result;
		}

		@Override
		public SendResult sendWithButtons(long chatId, String text, List<List<Button>> buttons) {
			throw new UnsupportedOperationException();
		}

		@Override
		public SendResult editText(long chatId, int messageId, String text) {
			throw new UnsupportedOperationException();
		}

		@Override
		public SendResult answerCallback(String callbackId) {
			answeredCallbacks.add(callbackId);
			return result;
		}
	}
}
