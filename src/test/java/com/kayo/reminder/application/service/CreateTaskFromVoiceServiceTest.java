package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kayo.reminder.application.component.DraftStore;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.domain.models.enums.SendFailureReason;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.LogCapture;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.SentButtons;
import com.kayo.reminder.testsupport.SentText;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import com.kayo.reminder.transport.llm.TaskParser;
import com.kayo.reminder.transport.stt.Transcriber;
import com.kayo.reminder.transport.telegram.AudioDownloader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateTaskFromVoiceServiceTest {

	private static final long CHAT = 100L;
	private static final int MESSAGE = 55;
	private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final Instant SIX_PM = Instant.parse("2026-11-22T21:00:00Z");
	private static final String TRANSCRIPT = "preciso enviar o relatório até as 18h, é urgente";
	private static final String TITLE = "Enviar o relatório";

	private final MutableClock clock = new MutableClock(NOW);
	private final RecordingSender sender = new RecordingSender();
	private final AudioDownloader download = mock(AudioDownloader.class);
	private final Transcriber transcription = mock(Transcriber.class);
	private final TaskParser parser = mock(TaskParser.class);
	private final TaskService repository = mock(TaskService.class);
	private CreateTaskFromVoiceService service;

	@BeforeEach
	void setUp() {
		service = service("en", 60);
		when(download.download("file-1")).thenReturn(new byte[] { 1, 2, 3 });
		when(transcription.transcribe(any())).thenReturn(TRANSCRIPT);
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, SIX_PM));
	}

	private CreateTaskFromVoiceService service(String language, int maxSeconds) {
		return new CreateTaskFromVoiceService(sender, download, transcription, parser, repository,
				new DraftStore(clock, Duration.ofMinutes(10)), TestMessages.of(language),
				TestProperties.of(ZONE, language, maxSeconds, Duration.ofMinutes(10)), clock, new TaskFactory(clock));
	}

	private static String en(String key, Object... args) {
		return TestMessages.text("en", key, args);
	}

	private static IncomingCommandRequest voice(int seconds) {
		return IncomingCommandRequest.voice(CHAT, 42, 7, "file-1", seconds);
	}

	private static IncomingCommandRequest text(String text) {
		return IncomingCommandRequest.text(CHAT, 42, 7, text);
	}

	private UUID draftFromVoice() {
		service.submit(voice(7));
		return draftIdOfLastConfirmation();
	}

	private UUID draftIdOfLastConfirmation() {
		SentButtons sent = sender.withButtons.get(sender.withButtons.size() - 1);
		String data = sent.buttons().get(0).get(0).callbackData();
		assertThat(data).startsWith(CreateTaskFromVoiceService.CONFIRM_PREFIX);
		return UUID.fromString(data.substring(CreateTaskFromVoiceService.CONFIRM_PREFIX.length()));
	}

	private TaskResponse savedTask() {
		ArgumentCaptor<TaskResponse> captor = ArgumentCaptor.forClass(TaskResponse.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void voiceNoteProducesTheSummaryWithButtonsAndSavesNothing() {
		service.submit(voice(7));

		assertThat(sender.withButtons).hasSize(1);
		SentButtons sent = sender.withButtons.get(0);
		assertThat(sent.chatId()).isEqualTo(CHAT);
		assertThat(sent.text()).contains(TITLE).contains("High").contains("2026-11-22 18:00")
				.doesNotContain("assumed");
		List<ButtonResponse> row = sent.buttons().get(0);
		assertThat(row).extracting(ButtonResponse::text).containsExactly("Confirm", "Cancel");
		assertThat(row.get(0).callbackData()).startsWith("task:ok:");
		assertThat(row.get(1).callbackData()).startsWith("task:no:");
		verify(transcription).transcribe(new byte[] { 1, 2, 3 });
		verify(parser).parse(TRANSCRIPT);
		verifyNoInteractions(repository);
	}

	@Test
	void plainTextSkipsDownloadAndTranscription() {
		service.submit(text("pay rent tomorrow 9am"));

		verifyNoInteractions(download, transcription);
		verify(parser).parse("pay rent tomorrow 9am");
		assertThat(sender.withButtons).hasSize(1);
	}

	@Test
	void missingUrgencyDefaultsToMediumAndSaysSo() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, null, null, SIX_PM));
		UUID id = draftFromVoice();

		assertThat(sender.withButtons.get(0).text()).contains("Medium (assumed");
		service.confirm(CHAT, MESSAGE, id);
		assertThat(savedTask().urgency()).isEqualTo(Urgency.MEDIUM);
	}

	@Test
	void startTimeIsShownWhenGiven() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.LOW, NOW.plusSeconds(1800), SIX_PM));
		service.submit(voice(7));
		assertThat(sender.withButtons.get(0).text()).contains("Start: 2026-11-22 10:30").contains("Low");
	}

	@Test
	void repliesInPortuguese() {
		service = service("pt-BR", 60);
		service.submit(voice(7));
		assertThat(sender.withButtons.get(0).text()).contains("Confirme a tarefa").contains("Alta");
		assertThat(sender.withButtons.get(0).buttons().get(0)).extracting(ButtonResponse::text)
				.containsExactly("Confirmar", "Cancelar");
	}

	@Test
	void tooLongAudioIsRejectedBeforeAnyDownload() {
		service.submit(voice(61));

		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.voice-too-long", 60));
		assertThat(sender.texts.get(0).text()).contains("60");
		verifyNoInteractions(download, transcription, parser, repository);
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void audioExactlyAtTheLimitIsAccepted() {
		service.submit(voice(60));
		assertThat(sender.withButtons).hasSize(1);
	}

	@Test
	void missingDeadlineAsksToResendAndCreatesNoDraft() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse("Comprar leite", Urgency.LOW, null, null));
		service.submit(text("comprar leite"));

		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.deadline-missing"));
		assertThat(sender.withButtons).isEmpty();
		verifyNoInteractions(repository);
	}

	@Test
	void deadlineAlreadyPassedAsksToResendAndCreatesNoDraft() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, NOW.minusSeconds(7200)));
		service.submit(text("enviar o relatório até as 8h"));

		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.deadline-past"));
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void deadlineEqualToNowIsPast() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, NOW));
		service.submit(text("agora"));
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.deadline-past"));
	}

	@Test
	void downloadFailureGivesAFriendlyMessageAndNoDraft() {
		when(download.download(anyString())).thenThrow(new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, "Telegram download"));
		service.submit(voice(7));
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.audio-failed"));
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void transcriptionUnavailableGivesAFriendlyMessageAndNoDraft() {
		when(transcription.transcribe(any())).thenThrow(new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, "Transcription"));
		service.submit(voice(7));
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.audio-failed"));
		assertThat(sender.withButtons).isEmpty();
		verifyNoInteractions(parser);
	}

	@Test
	void emptyTranscriptSaysNothingWasHeard() {
		when(transcription.transcribe(any())).thenThrow(new ExternalServiceException(ExternalFailureKind.INVALID_RESPONSE, "Transcription"));
		service.submit(voice(7));
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.no-speech"));
	}

	@Test
	void invalidModelAnswerAsksToRephrase() {
		when(parser.parse(anyString())).thenThrow(new ExternalServiceException(ExternalFailureKind.INVALID_RESPONSE, "Task parser"));
		service.submit(text("blah"));
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.not-understood"));
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void parserUnavailableAsksToTryAgain() {
		when(parser.parse(anyString())).thenThrow(new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, "Task parser"));
		service.submit(text("blah"));
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.parse-failed"));
	}

	@Test
	void failureMessagesNeverExposeTechnicalDetails() {
		when(parser.parse(anyString())).thenThrow(new ExternalServiceException(ExternalFailureKind.REJECTED, "Task parser"));
		service.submit(text("blah"));
		assertThat(sender.texts.get(0).text()).doesNotContain("Kind").doesNotContain("REJECTED").doesNotContain("Exception");
	}

	@Test
	void confirmSavesExactlyOnePendingTaskForTheChat() {
		UUID id = draftFromVoice();
		service.confirm(CHAT, MESSAGE, id);

		TaskResponse task = savedTask();
		assertThat(task.id()).isEqualTo(id);
		assertThat(task.chatId()).isEqualTo(CHAT);
		assertThat(task.title()).isEqualTo(TITLE);
		assertThat(task.urgency()).isEqualTo(Urgency.HIGH);
		assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(task.deadlineAt()).isEqualTo(SIX_PM);
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.edits.get(0).messageId()).isEqualTo(MESSAGE);
		assertThat(sender.edits.get(0).text()).startsWith(en("task.saved-header")).contains(TITLE);
	}

	@Test
	void inPortugueseTheDatesAreDayFirstAndTheSavedMessageIsFriendly() {
		service = service("pt-BR", 60);
		UUID id = draftFromVoice();
		String confirmation = sender.withButtons.get(0).text();
		service.confirm(CHAT, MESSAGE, id);

		assertThat(confirmation).startsWith("Está certo? Confirme a tarefa:")
				.contains("\uD83D\uDCDD " + TITLE).contains("\uD83D\uDD34 Urgência: Alta")
				.contains("\u23F0 Prazo: 22/11/2026 18:00").doesNotContain("2026-11-22");
		String saved = sender.edits.get(0).text();
		assertThat(saved).startsWith("Tarefa salva \u2705\n\n\uD83D\uDCDD " + TITLE)
				.contains("Prazo: 22/11/2026 18:00").endsWith("Eu aviso você quando o prazo estiver chegando.");
	}

	private void parsedAs(String language) {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, SIX_PM, language));
	}

	@Test
	void anEnglishMessageIsAnsweredInEnglishEvenWhenTheConfiguredLanguageIsPortuguese() {
		service = service("pt-BR", 60);
		parsedAs("en");
		UUID id = draftFromVoice();
		SentButtons asked = sender.withButtons.get(0);
		service.confirm(CHAT, MESSAGE, id);

		assertThat(asked.text()).startsWith(en("task.confirm-header")).contains("Urgency: ").contains("2026-11-22 18:00");
		assertThat(asked.buttons().get(0)).extracting(ButtonResponse::text).containsExactly("Confirm", "Cancel");
		assertThat(sender.edits.get(0).text()).startsWith("Task saved").contains("Due: 2026-11-22 18:00");
	}

	@Test
	void aPortugueseMessageIsAnsweredInPortugueseEvenWhenTheConfiguredLanguageIsEnglish() {
		parsedAs("pt-BR");
		UUID id = draftFromVoice();
		SentButtons asked = sender.withButtons.get(0);
		service.confirm(CHAT, MESSAGE, id);

		assertThat(asked.text()).startsWith("Está certo?").contains("Urgência: Alta").contains("Prazo: 22/11/2026 18:00");
		assertThat(asked.buttons().get(0)).extracting(ButtonResponse::text).containsExactly("Confirmar", "Cancelar");
		assertThat(sender.edits.get(0).text()).startsWith("Tarefa salva").endsWith("estiver chegando.");
	}

	@Test
	void cancelingAPortugueseDraftAnswersInPortuguese() {
		parsedAs("pt-BR");
		UUID id = draftFromVoice();
		service.cancel(CHAT, MESSAGE, id);

		assertThat(sender.edits.get(0).text()).startsWith("Tarefa cancelada");
	}

	@Test
	void aMessageWithNoKnownLanguageUsesTheConfiguredOne() {
		service = service("pt-BR", 60);
		parsedAs(null);
		draftFromVoice();
		assertThat(sender.withButtons.get(0).text()).startsWith("Está certo?");
	}

	@Test
	void missingOrPastDeadlinesAreAnsweredInTheMessagesLanguage() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, null, "pt-BR"));
		service.submit(text("comprar leite"));
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, NOW.minusSeconds(60), "pt-BR"));
		service.submit(text("enviar às 8h"));

		assertThat(sender.texts).extracting(SentText::text).containsExactly(
				TestMessages.text("pt-BR", "task.deadline-missing"), TestMessages.text("pt-BR", "task.deadline-past"));
	}

	@Test
	void failuresBeforeTheLanguageIsKnownUseTheConfiguredLanguage() {
		service = service("pt-BR", 60);
		when(transcription.transcribe(any())).thenThrow(new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, "Transcription"));
		service.submit(voice(7));
		assertThat(sender.texts.get(0).text()).isEqualTo(TestMessages.text("pt-BR", "task.audio-failed"));
	}

	@Test
	void inEnglishTheDateKeepsTheIsoFormat() {
		UUID id = draftFromVoice();
		service.confirm(CHAT, MESSAGE, id);

		assertThat(sender.edits.get(0).text()).contains("\u23F0 Due: 2026-11-22 18:00")
				.endsWith("I'll remind you before it's due.");
	}

	@Test
	void confirmAfterTheDeadlinePassedSavesNothing() {
		when(parser.parse(anyString())).thenReturn(new ParsedTaskResponse(TITLE, Urgency.HIGH, null, NOW.plusSeconds(30)));
		service.submit(text("daqui a 30 segundos"));
		UUID id = draftIdOfLastConfirmation();
		clock.advance(Duration.ofSeconds(31));

		service.confirm(CHAT, MESSAGE, id);

		verify(repository, never()).save(any());
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.edits.get(0).text()).isEqualTo(en("task.deadline-past"));
		service.confirm(CHAT, MESSAGE, id);
		verify(repository, never()).save(any());
	}

	@Test
	void cancelDiscardsTheDraftAndALaterConfirmSavesNothing() {
		UUID id = draftFromVoice();
		service.cancel(CHAT, MESSAGE, id);

		assertThat(sender.edits).hasSize(1);
		assertThat(sender.edits.get(0).text()).startsWith(en("task.canceled-header"));
		service.confirm(CHAT, MESSAGE, id);
		verify(repository, never()).save(any());
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.draft-gone"));
	}

	@Test
	void doubleConfirmSavesOnceAndAnswersTheSecondPress() {
		UUID id = draftFromVoice();
		service.confirm(CHAT, MESSAGE, id);
		service.confirm(CHAT, MESSAGE, id);

		verify(repository, times(1)).save(any());
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.draft-gone"));
	}

	@Test
	void confirmAtTheTtlBoundaries() {
		UUID inside = draftFromVoice();
		clock.advance(Duration.ofMinutes(9).plusSeconds(59));
		service.confirm(CHAT, MESSAGE, inside);
		verify(repository, times(1)).save(any());

		clock.advance(Duration.ofMinutes(20));
		UUID late = draftFromVoice();
		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		service.confirm(CHAT, MESSAGE, late);
		verify(repository, times(1)).save(any());
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.draft-gone"));
	}

	@Test
	void unknownDraftIdSavesNothing() {
		service.confirm(CHAT, MESSAGE, UUID.randomUUID());
		service.cancel(CHAT, MESSAGE, UUID.randomUUID());
		verifyNoInteractions(repository);
		assertThat(sender.texts).hasSize(2);
	}

	@Test
	void twoDraftsAreIndependent() {
		UUID first = draftFromVoice();
		UUID second = draftFromVoice();
		assertThat(first).isNotEqualTo(second);

		service.confirm(CHAT, MESSAGE, second);

		assertThat(savedTask().id()).isEqualTo(second);
		service.confirm(CHAT, MESSAGE, first);
		verify(repository, times(2)).save(any());
	}

	@Test
	void saveFailureKeepsTheDraftSoALaterConfirmSavesOnce() {
		UUID id = draftFromVoice();
		doThrow(new IllegalStateException("db down")).doNothing().when(repository).save(any());
		when(repository.findById(any())).thenReturn(Optional.empty());

		service.confirm(CHAT, MESSAGE, id);
		assertThat(sender.texts).extracting(SentText::text).containsExactly(en("task.save-failed"));
		assertThat(sender.edits).isEmpty();

		service.confirm(CHAT, MESSAGE, id);
		verify(repository, times(2)).save(any());
		assertThat(sender.edits).hasSize(1);
		assertThat(sender.edits.get(0).text()).startsWith(en("task.saved-header"));

		service.confirm(CHAT, MESSAGE, id);
		verify(repository, times(2)).save(any());
	}

	@Test
	void saveThatActuallyCommittedIsTreatedAsSaved() {
		UUID id = draftFromVoice();
		doThrow(new IllegalStateException("connection reset after commit")).when(repository).save(any());
		when(repository.findById(id)).thenReturn(Optional.of(mock(TaskResponse.class)));

		service.confirm(CHAT, MESSAGE, id);

		assertThat(sender.edits.get(0).text()).startsWith(en("task.saved-header"));
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void failedEditFallsBackToANewMessage() {
		UUID id = draftFromVoice();
		sender.result = com.kayo.reminder.application.dto.response.SendResultResponse.failed(
				com.kayo.reminder.domain.models.enums.SendFailureReason.REJECTED);
		service.confirm(CHAT, MESSAGE, id);
		assertThat(sender.texts).extracting(SentText::text).hasSize(1);
		assertThat(sender.texts.get(0).text()).startsWith(en("task.saved-header"));
	}

	@Test
	void anotherChatCannotConfirmTheDraft() {
		UUID id = draftFromVoice();
		service.confirm(CHAT + 1, MESSAGE, id);
		verifyNoInteractions(repository);
		service.confirm(CHAT, MESSAGE, id);
		verify(repository).save(any());
	}

	@Test
	void theWholeFlowLogsNoTranscriptTitleKeyOrToken() {
		try (LogCapture logs = new LogCapture(CreateTaskFromVoiceService.class)) {
			UUID id = draftFromVoice();
			service.confirm(CHAT, MESSAGE, id);
			when(parser.parse(anyString())).thenThrow(new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, "Task parser"));
			service.submit(text("segredo do usuário"));
			when(transcription.transcribe(any())).thenThrow(new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, "Transcription"));
			service.submit(voice(7));

			assertThat(logs.events()).isNotEmpty();
			assertThat(logs.allText()).doesNotContain(TRANSCRIPT).doesNotContain(TITLE).doesNotContain("relatório")
					.doesNotContain("segredo").doesNotContain("llm-key").doesNotContain("groq-key")
					.doesNotContain(TestProperties.BOT_TOKEN);
		}
	}
}
