package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.DraftStore;
import com.kayo.reminder.application.component.MessageReplacer;
import com.kayo.reminder.application.component.Messages;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.component.TaskSummaryFormatter;
import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.DraftResponse;
import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.exceptions.TaskValidationException;
import com.kayo.reminder.domain.models.enums.CommandKind;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.transport.llm.TaskParser;
import com.kayo.reminder.transport.stt.Transcriber;
import com.kayo.reminder.transport.telegram.AudioDownloader;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CreateTaskFromVoiceService {

	public static final String CONFIRM_PREFIX = "task:ok:";
	public static final String CANCEL_PREFIX = "task:no:";

	private static final Logger log = LoggerFactory.getLogger(CreateTaskFromVoiceService.class);

	private final MessageSender sender;
	private final AudioDownloader audioDownload;
	private final Transcriber transcription;
	private final TaskParser parser;
	private final TaskService tasks;
	private final DraftStore drafts;
	private final Messages messages;
	private final TaskSummaryFormatter formatter;
	private final int maxVoiceSeconds;
	private final Clock clock;
	private final TaskFactory taskFactory;

	public CreateTaskFromVoiceService(MessageSender sender, AudioDownloader audioDownload,
			Transcriber transcription, TaskParser parser, TaskService tasks, DraftStore drafts,
			Messages messages, AppProperties properties, Clock clock, TaskFactory taskFactory) {
		this.sender = sender;
		this.audioDownload = audioDownload;
		this.transcription = transcription;
		this.parser = parser;
		this.tasks = tasks;
		this.drafts = drafts;
		this.messages = messages;
		this.formatter = new TaskSummaryFormatter(properties.timezone());
		this.maxVoiceSeconds = properties.voice().maxSeconds();
		this.clock = clock;
		this.taskFactory = taskFactory;
	}

	public void submit(IncomingCommandRequest command) {
		textOf(command).ifPresent(text -> propose(command.chatId(), text));
	}

	public void confirm(long chatId, Integer messageId, UUID draftId) {
		drafts.take(draftId, chatId).ifPresentOrElse(draft -> save(chatId, messageId, draft),
				() -> sender.sendText(chatId, messages.get("task.draft-gone")));
	}

	public void cancel(long chatId, Integer messageId, UUID draftId) {
		drafts.take(draftId, chatId).ifPresentOrElse(draft -> discard(chatId, messageId, draft),
				() -> sender.sendText(chatId, messages.get("task.draft-gone")));
	}

	private Optional<String> textOf(IncomingCommandRequest command) {
		return command.kind() == CommandKind.VOICE ? transcribe(command) : Optional.ofNullable(command.text());
	}

	private Optional<String> transcribe(IncomingCommandRequest command) {
		return withinLimit(command).flatMap(this::download)
				.flatMap(audio -> speechToText(command.chatId(), audio));
	}

	private Optional<IncomingCommandRequest> withinLimit(IncomingCommandRequest command) {
		int seconds = Optional.ofNullable(command.voiceDurationSeconds()).orElse(0);
		return Optional.of(command).filter(voice -> seconds <= maxVoiceSeconds)
				.or(() -> refuse(command.chatId(), "task.voice-too-long", maxVoiceSeconds));
	}

	private Optional<byte[]> download(IncomingCommandRequest command) {
		try {
			return Optional.of(audioDownload.download(command.voiceFileId()));
		}
		catch (ExternalServiceException failure) {
			log.warn("Downloading a voice note for chat {} failed: {}", command.chatId(), failure.kind());
			return refuse(command.chatId(), "task.audio-failed");
		}
	}

	private Optional<String> speechToText(long chatId, byte[] audio) {
		try {
			return Optional.of(transcription.transcribe(audio));
		}
		catch (ExternalServiceException failure) {
			log.warn("Transcribing a voice note for chat {} failed: {}", chatId, failure.kind());
			return refuse(chatId, failure.kind() == ExternalFailureKind.INVALID_RESPONSE ? "task.no-speech" : "task.audio-failed");
		}
	}

	private void propose(long chatId, String text) {
		parse(chatId, text).ifPresent(parsed -> {
			Messages texts = messages.forLanguage(parsed.language());
			deadlineProblem(parsed).ifPresentOrElse(key -> sender.sendText(chatId, texts.get(key)),
					() -> sendDraft(chatId, parsed, texts));
		});
	}

	private Optional<ParsedTaskResponse> parse(long chatId, String text) {
		try {
			return Optional.of(parser.parse(text));
		}
		catch (ExternalServiceException failure) {
			log.warn("Parsing a task for chat {} failed: {}", chatId, failure.kind());
			return refuse(chatId, failure.kind() == ExternalFailureKind.INVALID_RESPONSE ? "task.not-understood" : "task.parse-failed");
		}
	}

	private Optional<String> deadlineProblem(ParsedTaskResponse parsed) {
		return Optional.of(parsed).filter(task -> task.deadlineAt() == null).map(task -> "task.deadline-missing")
				.or(() -> Optional.of(parsed).filter(task -> !task.deadlineAt().isAfter(clock.instant()))
						.map(task -> "task.deadline-past"));
	}

	private void sendDraft(long chatId, ParsedTaskResponse parsed, Messages texts) {
		boolean assumed = parsed.urgency() == null;
		DraftResponse draft = drafts.create(chatId, parsed.title(), Optional.ofNullable(parsed.urgency()).orElse(Urgency.MEDIUM),
				assumed, parsed.startAt(), parsed.deadlineAt(), texts.languageTag());
		log.info("Draft {} created for chat {}", draft.id(), chatId);
		sender.sendWithButtons(chatId, texts.get("task.confirm-header") + "\n\n" + summary(texts, draft),
				List.of(List.of(
						new ButtonResponse(texts.get("task.confirm-button"), CONFIRM_PREFIX + draft.id()),
						new ButtonResponse(texts.get("task.cancel-button"), CANCEL_PREFIX + draft.id()))));
	}

	private void save(long chatId, Integer messageId, DraftResponse draft) {
		Messages texts = messages.forLanguage(draft.language());
		createTask(chatId, messageId, draft, texts).ifPresent(task -> store(chatId, messageId, draft, texts, task));
	}

	private Optional<TaskResponse> createTask(long chatId, Integer messageId, DraftResponse draft, Messages texts) {
		try {
			return Optional.of(taskFactory.create(new CreateTaskRequest(draft.id(), chatId, draft.title(), draft.urgency(),
					draft.startAt(), draft.deadlineAt(), draft.language())));
		}
		catch (TaskValidationException invalid) {
			log.info("Draft {} could not become a task: {}", draft.id(), invalid.rule());
			show(chatId, messageId, texts.get("task.deadline-past"));
			return Optional.empty();
		}
	}

	private void store(long chatId, Integer messageId, DraftResponse draft, Messages texts, TaskResponse task) {
		Optional.of(task).filter(this::saved).ifPresentOrElse(stored -> {
			log.info("Task {} saved for chat {}", stored.id(), chatId);
			show(chatId, messageId, texts.get("task.saved-header") + "\n\n" + summary(texts, draft)
					+ "\n\n" + texts.get("task.saved-footer"));
		}, () -> {
			drafts.restore(draft);
			sender.sendText(chatId, texts.get("task.save-failed"));
		});
	}

	private boolean saved(TaskResponse task) {
		try {
			tasks.save(task);
			return true;
		}
		catch (RuntimeException failure) {
			log.warn("Saving task {} failed: {}", task.id(), failure.getClass().getSimpleName());
			return alreadyStored(task.id());
		}
	}

	private boolean alreadyStored(UUID id) {
		try {
			return tasks.findById(id).isPresent();
		}
		catch (RuntimeException failure) {
			return false;
		}
	}

	private void discard(long chatId, Integer messageId, DraftResponse draft) {
		log.info("Draft {} canceled", draft.id());
		Messages texts = messages.forLanguage(draft.language());
		show(chatId, messageId, texts.get("task.canceled-header") + "\n\n" + summary(texts, draft));
	}

	private String summary(Messages texts, DraftResponse draft) {
		return formatter.summary(texts, draft);
	}

	private <T> Optional<T> refuse(long chatId, String key, Object... args) {
		sender.sendText(chatId, messages.get(key, args));
		return Optional.empty();
	}

	private void show(long chatId, Integer messageId, String text) {
		MessageReplacer.show(sender, chatId, messageId, text);
	}
}
