package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.CreateTaskFromVoiceUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.AudioDownloadPort;
import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.ParsedTask;
import com.kayo.reminder.application.port.out.TaskParserPort;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.application.port.out.TranscriptionPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskValidationException;
import com.kayo.reminder.domain.Urgency;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Voice note or text → parsed task → draft with Confirm/Cancel → saved task. Only ids, durations and failure kinds
 * are logged: never audio, transcripts, task text or credentials.
 */
@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CreateTaskFromVoiceService implements CreateTaskFromVoiceUseCase {

	private static final Logger log = LoggerFactory.getLogger(CreateTaskFromVoiceService.class);

	private final MessageSenderPort sender;
	private final AudioDownloadPort audioDownload;
	private final TranscriptionPort transcription;
	private final TaskParserPort parser;
	private final TaskRepository tasks;
	private final DraftStore drafts;
	private final Messages messages;
	private final java.time.ZoneId zone;
	private final int maxVoiceSeconds;
	private final Clock clock;

	public CreateTaskFromVoiceService(MessageSenderPort sender, AudioDownloadPort audioDownload,
			TranscriptionPort transcription, TaskParserPort parser, TaskRepository tasks, DraftStore drafts,
			Messages messages, AppProperties properties, Clock clock) {
		this.sender = sender;
		this.audioDownload = audioDownload;
		this.transcription = transcription;
		this.parser = parser;
		this.tasks = tasks;
		this.drafts = drafts;
		this.messages = messages;
		this.zone = properties.timezone();
		this.maxVoiceSeconds = properties.voice().maxSeconds();
		this.clock = clock;
	}

	@Override
	public void submit(IncomingCommand command) {
		long chatId = command.chatId();
		String text = command.kind() == IncomingCommand.Kind.VOICE ? transcribe(command) : command.text();
		if (text == null) {
			return;
		}
		ParsedTask parsed;
		try {
			parsed = parser.parse(text);
		}
		catch (ExternalServiceException ex) {
			log.warn("Parsing a task for chat {} failed: {}", chatId, ex.kind());
			sender.sendText(chatId, messages.get(ex.kind() == Kind.INVALID_RESPONSE ? "task.not-understood"
					: "task.parse-failed"));
			return;
		}
		// From here on the owner is answered in the language the message was written in.
		Messages texts = messages.forLanguage(parsed.language());
		if (parsed.deadlineAt() == null) {
			sender.sendText(chatId, texts.get("task.deadline-missing"));
			return;
		}
		if (!parsed.deadlineAt().isAfter(clock.instant())) {
			sender.sendText(chatId, texts.get("task.deadline-past"));
			return;
		}
		boolean assumed = parsed.urgency() == null;
		Draft draft = drafts.create(chatId, parsed.title(), assumed ? Urgency.MEDIUM : parsed.urgency(), assumed,
				parsed.startAt(), parsed.deadlineAt(), parsed.language());
		log.info("Draft {} created for chat {}", draft.id(), chatId);
		sender.sendWithButtons(chatId, texts.get("task.confirm-header") + "\n\n" + summary(texts, draft),
				List.of(List.of(
						new Button(texts.get("task.confirm-button"), CONFIRM_PREFIX + draft.id()),
						new Button(texts.get("task.cancel-button"), CANCEL_PREFIX + draft.id()))));
	}

	/** Returns the transcript, or null after telling the owner why there is none. */
	private String transcribe(IncomingCommand command) {
		long chatId = command.chatId();
		int seconds = command.voiceDurationSeconds() == null ? 0 : command.voiceDurationSeconds();
		if (seconds > maxVoiceSeconds) {
			sender.sendText(chatId, messages.get("task.voice-too-long", maxVoiceSeconds));
			return null;
		}
		byte[] audio;
		try {
			audio = audioDownload.download(command.voiceFileId());
		}
		catch (ExternalServiceException ex) {
			log.warn("Downloading a voice note for chat {} failed: {}", chatId, ex.kind());
			sender.sendText(chatId, messages.get("task.audio-failed"));
			return null;
		}
		try {
			return transcription.transcribe(audio);
		}
		catch (ExternalServiceException ex) {
			log.warn("Transcribing a voice note for chat {} failed: {}", chatId, ex.kind());
			sender.sendText(chatId, messages.get(ex.kind() == Kind.INVALID_RESPONSE ? "task.no-speech"
					: "task.audio-failed"));
			return null;
		}
	}

	@Override
	public void confirm(long chatId, Integer messageId, UUID draftId) {
		Draft draft = drafts.take(draftId, chatId).orElse(null);
		if (draft == null) {
			sender.sendText(chatId, messages.get("task.draft-gone"));
			return;
		}
		Messages texts = messages.forLanguage(draft.language());
		Instant now = clock.instant();
		Task task;
		try {
			task = Task.create(draft.id(), chatId, draft.title(), draft.urgency(), draft.startAt(),
					draft.deadlineAt(), now);
		}
		catch (TaskValidationException ex) {
			log.info("Draft {} could not become a task: {}", draft.id(), ex.rule());
			show(chatId, messageId, texts.get("task.deadline-past"));
			return;
		}
		try {
			tasks.save(task);
		}
		catch (RuntimeException ex) {
			log.warn("Saving task {} failed: {}", task.id(), ex.getClass().getSimpleName());
			if (!alreadyStored(task.id())) {
				drafts.restore(draft);
				sender.sendText(chatId, texts.get("task.save-failed"));
				return;
			}
		}
		log.info("Task {} saved for chat {}", task.id(), chatId);
		show(chatId, messageId, texts.get("task.saved-header") + "\n\n" + summary(texts, draft)
				+ "\n\n" + texts.get("task.saved-footer"));
	}

	@Override
	public void cancel(long chatId, Integer messageId, UUID draftId) {
		Draft draft = drafts.take(draftId, chatId).orElse(null);
		if (draft == null) {
			sender.sendText(chatId, messages.get("task.draft-gone"));
			return;
		}
		log.info("Draft {} canceled", draft.id());
		Messages texts = messages.forLanguage(draft.language());
		show(chatId, messageId, texts.get("task.canceled-header") + "\n\n" + summary(texts, draft));
	}

	private String summary(Messages texts, Draft draft) {
		return new TaskSummaryFormatter(texts, zone).summary(draft);
	}

	/** A retry after a save that did commit finds the row already there: that is a success, not a failure. */
	private boolean alreadyStored(UUID id) {
		try {
			return tasks.findById(id).isPresent();
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	/** Replaces the confirmation message (removing its buttons); falls back to a new message if that fails. */
	private void show(long chatId, Integer messageId, String text) {
		if (messageId != null && sender.editText(chatId, messageId, text).success()) {
			return;
		}
		sender.sendText(chatId, text);
	}
}
