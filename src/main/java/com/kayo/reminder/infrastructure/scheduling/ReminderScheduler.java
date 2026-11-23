package com.kayo.reminder.infrastructure.scheduling;

import com.kayo.reminder.application.port.in.SendDueRemindersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the reminder job. {@code fixedDelay} waits for a run to finish before timing the next, so runs never overlap
 * however long a send takes.
 */
class ReminderScheduler {

	private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

	private final SendDueRemindersUseCase useCase;

	ReminderScheduler(SendDueRemindersUseCase useCase) {
		this.useCase = useCase;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.interval}")
	void tick() {
		try {
			useCase.sendDueReminders();
		} catch (RuntimeException e) {
			log.error("Reminder run failed: {}", e.getClass().getSimpleName(), e);
		}
	}
}
