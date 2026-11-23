package com.kayo.reminder.application.service;

import com.kayo.reminder.application.component.ReminderMessageFactory;
import com.kayo.reminder.application.dto.response.ReminderDecisionResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendDueRemindersService {

	private static final Logger log = LoggerFactory.getLogger(SendDueRemindersService.class);

	private final TaskService tasks;
	private final MessageSender sender;
	private final ReminderMessageFactory texts;
	private final ReminderPolicy policy;
	private final Clock clock;

	public SendDueRemindersService(TaskService tasks, MessageSender sender, ReminderMessageFactory texts,
			ReminderPolicy policy, Clock clock) {
		this.tasks = tasks;
		this.sender = sender;
		this.texts = texts;
		this.policy = policy;
		this.clock = clock;
	}

	public int sendDueReminders() {
		Instant now = clock.instant();
		return (int) tasks.findReminderCandidates(now.plus(ReminderPolicy.LOOKAHEAD), policy.finalStages()).stream()
				.filter(candidate -> remindSafely(candidate, now)).count();
	}

	private boolean remindSafely(TaskResponse candidate, Instant now) {
		try {
			return remind(candidate.id(), now);
		}
		catch (RuntimeException failure) {
			log.error("Reminder for task {} failed: {}", candidate.id(), failure.getClass().getSimpleName(), failure);
			return false;
		}
	}

	private boolean remind(UUID id, Instant now) {
		return tasks.findById(id)
				.map(task -> policy.decide(task, now).map(decision -> send(task, decision, now)).orElse(false))
				.orElse(false);
	}

	private boolean send(TaskResponse task, ReminderDecisionResponse decision, Instant now) {
		SendResultResponse result = sender.sendWithButtons(task.chatId(), texts.create(task, decision, now),
				texts.buttons(task));
		Optional.of(result).filter(sent -> !sent.success()).ifPresent(failed -> log
				.warn("Reminder stage {} for task {} not sent ({}); will retry", decision.stage(), task.id(),
						failed.failure()));
		return result.success() && record(task, decision, now);
	}

	private boolean record(TaskResponse task, ReminderDecisionResponse decision, Instant now) {
		Optional.of(task).filter(sent -> !tasks.markReminded(sent.id(), decision.stage(), now, sent.deadlineAt()))
				.ifPresent(changed -> log.warn(
						"Reminder stage {} for task {} was sent but the task changed before it could be recorded",
						decision.stage(), changed.id()));
		log.info("Reminder stage {} sent for task {}", decision.stage(), task.id());
		return true;
	}
}
