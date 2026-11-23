package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.domain.models.enums.SendFailureReason;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.SentText;
import com.kayo.reminder.testsupport.TestMessages;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncomingCommandServiceTest {

	private final RecordingSender sender = new RecordingSender();
	private final CreateTaskFromVoiceService createTask = mock(CreateTaskFromVoiceService.class);
	private final ReminderActionService reminderActions = mock(ReminderActionService.class);
	private final ListTasksService listTasks = mock(ListTasksService.class);

	private IncomingCommandService service(String languageTag) {
		return new IncomingCommandService(sender, TestMessages.of(languageTag), createTask, reminderActions, listTasks);
	}

	private static String english(String key) {
		return TestMessages.text("en", key);
	}

	private static String portuguese(String key) {
		return TestMessages.text("pt-BR", key);
	}

	@Test
	void startRepliesWithUsage() {
		service("en").handle(IncomingCommandRequest.text(10, 42, 1, "/start"));
		assertThat(sender.texts).containsExactly(new SentText(10, english("usage")));
		verifyNoInteractions(createTask);
	}

	@Test
	void helpRepliesWithUsage() {
		service("en").handle(IncomingCommandRequest.text(10, 42, 1, "/help"));
		assertThat(sender.texts).containsExactly(new SentText(10, english("usage")));
	}

	@Test
	void usageExplainsHowToCreateATask() {
		assertThat(english("usage")).contains("voice note").contains("confirm").doesNotContain("can't create");
	}

	@Test
	void commandAddressedToBotNameOrWithArgumentsIsTreatedAsBareCommand() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommandRequest.text(10, 42, 1, "/help@my_bot"));
		service.handle(IncomingCommandRequest.text(10, 42, 2, "/start@my_bot  some args"));
		service.handle(IncomingCommandRequest.text(10, 42, 3, "  /START now"));
		assertThat(sender.texts).containsOnly(new SentText(10, english("usage"))).hasSize(3);
		verifyNoInteractions(createTask);
	}

	@Test
	void plainTextStartsTheTaskFlow() {
		IncomingCommandRequest command = IncomingCommandRequest.text(10, 42, 1, "remind me tomorrow at 9");
		service("en").handle(command);
		verify(createTask).submit(command);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void textMentioningStartMidSentenceIsATask() {
		IncomingCommandRequest command = IncomingCommandRequest.text(10, 42, 1, "please /start");
		service("en").handle(command);
		verify(createTask).submit(command);
	}

	@Test
	void voiceStartsTheTaskFlow() {
		IncomingCommandRequest command = IncomingCommandRequest.voice(10, 42, 1, "file-1", 7);
		service("en").handle(command);
		verify(createTask).submit(command);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void unknownCommandGetsPlaceholder() {
		service("en").handle(IncomingCommandRequest.text(10, 42, 1, "/foo"));
		assertThat(sender.texts).containsExactly(new SentText(10, english("not-supported-yet")));
		verifyNoInteractions(createTask);
	}

	@Test
	void confirmButtonIsAcknowledgedAndHandedToTheFlow() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommandRequest.callback(10, 42, 77, "cb-1", "task:ok:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1");
		verify(createTask).confirm(10, 77, id);
		verifyNoMoreInteractions(createTask);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void cancelButtonIsAcknowledgedAndHandedToTheFlow() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommandRequest.callback(10, 42, 77, "cb-2", "task:no:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-2");
		verify(createTask).cancel(10, 77, id);
	}

	@Test
	void unknownOrMalformedButtonDataIsOnlyAcknowledged() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-1", "x:1"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-2", "task:ok:not-a-uuid"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-3", "task:no:"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-4", null));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1", "cb-2", "cb-3", "cb-4");
		assertThat(sender.texts).isEmpty();
		verify(createTask, never()).confirm(org.mockito.ArgumentMatchers.anyLong(), any(), any());
		verify(createTask, never()).cancel(org.mockito.ArgumentMatchers.anyLong(), any(), any());
	}

	@Test
	void doneButtonIsAcknowledgedAndHandedToTheReminderActions() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommandRequest.callback(10, 42, 77, "cb-1", "task:done:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1");
		verify(reminderActions).done(10, 77, id);
		verifyNoMoreInteractions(reminderActions);
		verifyNoInteractions(createTask);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void snoozeButtonIsAcknowledgedAndHandedToTheReminderActions() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommandRequest.callback(10, 42, 78, "cb-2", "task:snooze:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-2");
		verify(reminderActions).snooze(10, 78, id);
		verifyNoMoreInteractions(reminderActions);
	}

	@Test
	void cancelTaskButtonIsAcknowledgedAndHandedToTheReminderActions() {
		UUID id = UUID.randomUUID();
		service("en").handle(IncomingCommandRequest.callback(10, 42, 79, "cb-3", "task:cancel:" + id));
		assertThat(sender.answeredCallbacks).containsExactly("cb-3");
		verify(reminderActions).cancel(10, 79, id);
		verifyNoMoreInteractions(reminderActions);
		verifyNoInteractions(createTask);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void tasksCommandListsThePendingTasksOfTheChat() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommandRequest.text(10, 42, 1, "/tasks"));
		service.handle(IncomingCommandRequest.text(10, 42, 2, "/TASKS@my_bot"));
		service.handle(IncomingCommandRequest.text(10, 42, 3, "  /tasks now"));
		verify(listTasks, org.mockito.Mockito.times(3)).list(10);
		verifyNoInteractions(createTask);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void malformedReminderButtonDataIsOnlyAcknowledged() {
		IncomingCommandService service = service("en");
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-1", "task:done:not-a-uuid"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-2", "task:snooze:"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-3", "task:done"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-4", "task:cancel:not-a-uuid"));
		assertThat(sender.answeredCallbacks).containsExactly("cb-1", "cb-2", "cb-3", "cb-4");
		assertThat(sender.texts).isEmpty();
		verifyNoInteractions(reminderActions);
	}

	@Test
	void repliesInPortuguese() {
		IncomingCommandService service = service("pt-BR");
		service.handle(IncomingCommandRequest.text(10, 42, 1, "/start"));
		service.handle(IncomingCommandRequest.text(10, 42, 2, "/foo"));
		assertThat(sender.texts).containsExactly(
				new SentText(10, portuguese("usage")), new SentText(10, portuguese("not-supported-yet")));
		assertThat(portuguese("usage")).isNotEqualTo(english("usage"));
	}

	@Test
	void failedSendResultDoesNotThrow() {
		sender.result = SendResultResponse.failed(SendFailureReason.TRANSIENT);
		IncomingCommandService service = service("en");
		service.handle(IncomingCommandRequest.text(10, 42, 1, "/start"));
		service.handle(IncomingCommandRequest.callback(10, 42, 1, "cb-1", "x"));
		assertThat(sender.texts).hasSize(1);
		assertThat(sender.answeredCallbacks).hasSize(1);
	}
}
