package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.kayo.reminder.application.port.in.CreateTaskFromVoiceUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.SendResult.FailureReason;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncomingCommandServiceTest {

	private final RecordingSender sender = new RecordingSender();
	private final CreateTaskFromVoiceUseCase createTask = mock(CreateTaskFromVoiceUseCase.class);

	private IncomingCommandService service(String languageTag) {
		return new IncomingCommandService(sender, TestMessages.of(languageTag), createTask);
	}

	private static String english(String key) {
		return TestMessages.text("en", key);
	}

	private static String portuguese(String key) {
		return TestMessages.text("pt-BR", key);
	}

	private record Sent(long chatId, String text) {
	}

	private java.util.List<Sent> texts() {
		return sender.texts.stream().map(t -> new Sent(t.chatId(), t.text())).toList();
	}

	@Test
	void startRepliesWithUsage() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "/start"));
		assertThat(texts()).containsExactly(new Sent(10, english("usage")));
		verifyNoInteractions(createTask);
	}

	@Test
	void helpRepliesWithUsage() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "/help"));
		assertThat(texts()).containsExactly(new Sent(10, english("usage")));
	}

	@Test
	void usageExplainsHowToCreateATask() {
		assertThat(english("usage")).contains("voice note").contains("confirm").doesNotContain("can't create");
	}

	@Test
	void commandAddressedToBotNameOrWithArgumentsIsTreatedAsBareCommand() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommand.text(10, 42, 1, "/help@my_bot"));
		service.handle(IncomingCommand.text(10, 42, 2, "/start@my_bot  some args"));
		service.handle(IncomingCommand.text(10, 42, 3, "  /START now"));
		assertThat(texts()).containsOnly(new Sent(10, english("usage"))).hasSize(3);
		verifyNoInteractions(createTask);
	}

	@Test
	void plainTextStartsTheTaskFlow() {
		IncomingCommand command = IncomingCommand.text(10, 42, 1, "remind me tomorrow at 9");
		service("en").handle(command);
		verify(createTask).submit(command);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void textMentioningStartMidSentenceIsATask() {
		IncomingCommand command = IncomingCommand.text(10, 42, 1, "please /start");
		service("en").handle(command);
		verify(createTask).submit(command);
	}

	@Test
	void voiceStartsTheTaskFlow() {
		IncomingCommand command = IncomingCommand.voice(10, 42, 1, "file-1", 7);
		service("en").handle(command);
		verify(createTask).submit(command);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void unknownCommandGetsPlaceholder() {
		service("en").handle(IncomingCommand.text(10, 42, 1, "/foo"));
		assertThat(texts()).containsExactly(new Sent(10, english("not-supported-yet")));
		verifyNoInteractions(createTask);
	}

	@Test
	void confirmButtonIsAcknowledgedAndHandedToTheFlow() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommand.callback(10, 42, 77, "cb-1", "task:ok:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1");
		verify(createTask).confirm(10, 77, id);
		verifyNoMoreInteractions(createTask);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void cancelButtonIsAcknowledgedAndHandedToTheFlow() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommand.callback(10, 42, 77, "cb-2", "task:no:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-2");
		verify(createTask).cancel(10, 77, id);
	}

	@Test
	void unknownOrMalformedButtonDataIsOnlyAcknowledged() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommand.callback(10, 42, 1, "cb-1", "x:1"));
		service.handle(IncomingCommand.callback(10, 42, 1, "cb-2", "task:ok:not-a-uuid"));
		service.handle(IncomingCommand.callback(10, 42, 1, "cb-3", "task:no:"));
		service.handle(IncomingCommand.callback(10, 42, 1, "cb-4", null));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1", "cb-2", "cb-3", "cb-4");
		assertThat(sender.texts).isEmpty();
		verify(createTask, never()).confirm(org.mockito.ArgumentMatchers.anyLong(), any(), any());
		verify(createTask, never()).cancel(org.mockito.ArgumentMatchers.anyLong(), any(), any());
	}

	@Test
	void repliesInPortuguese() {
		IncomingCommandService service = service("pt-BR");
		service.handle(IncomingCommand.text(10, 42, 1, "/start"));
		service.handle(IncomingCommand.text(10, 42, 2, "/foo"));
		assertThat(texts()).containsExactly(
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
}
