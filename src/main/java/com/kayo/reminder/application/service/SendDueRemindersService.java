package com.kayo.reminder.application.service;

import com.kayo.reminder.application.port.in.SendDueRemindersUseCase;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.SendResult;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.ReminderDecision;
import com.kayo.reminder.domain.ReminderPolicy;
import com.kayo.reminder.domain.Task;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Sends first and records after: the stage is stored only once the message went out, so a failed send is retried on
 * the next run and a stored stage is never repeated. The send happens outside any database transaction because the
 * sender may wait through retries. Only ids, stages and failure kinds are logged: never the title or credentials.
 */
@Service
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SendDueRemindersService implements SendDueRemindersUseCase {

	private static final Logger log = LoggerFactory.getLogger(SendDueRemindersService.class);

	private final TaskRepository tasks;
	private final MessageSenderPort sender;
	private final ReminderMessageFactory texts;
	private final ReminderPolicy policy;
	private final Clock clock;

	public SendDueRemindersService(TaskRepository tasks, MessageSenderPort sender, ReminderMessageFactory texts,
			ReminderPolicy policy, Clock clock) {
		this.tasks = tasks;
		this.sender = sender;
		this.texts = texts;
		this.policy = policy;
		this.clock = clock;
	}

	@Override
	public int sendDueReminders() {
		Instant now = clock.instant();
		int sent = 0;
		for (Task candidate : tasks.findReminderCandidates(now.plus(ReminderPolicy.LOOKAHEAD))) {
			try {
				if (remind(candidate.id(), now)) {
					sent++;
				}
			} catch (RuntimeException e) {
				log.error("Reminder for task {} failed: {}", candidate.id(), e.getClass().getSimpleName(), e);
			}
		}
		return sent;
	}

	/** Re-reads the task first, since it may have changed (done, canceled) since the candidate list was loaded. */
	private boolean remind(UUID id, Instant now) {
		Optional<Task> current = tasks.findById(id);
		Optional<ReminderDecision> decision = current.flatMap(task -> policy.decide(task, now));
		if (decision.isEmpty()) {
			return false;
		}
		Task task = current.get();
		int stage = decision.get().stage();
		SendResult result = sender.sendWithButtons(task.chatId(), texts.create(task, decision.get(), now), texts.buttons(task));
		if (!result.success()) {
			log.warn("Reminder stage {} for task {} not sent ({}); will retry", stage, id, result.failure());
			return false;
		}
		if (!tasks.markReminded(id, stage, now, task.deadlineAt())) {
			log.warn("Reminder stage {} for task {} was sent but the task changed before it could be recorded", stage,
					id);
		}
		log.info("Reminder stage {} sent for task {}", stage, id);
		return true;
	}
}
