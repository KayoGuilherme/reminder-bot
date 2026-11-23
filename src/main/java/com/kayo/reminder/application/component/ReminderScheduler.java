package com.kayo.reminder.application.component;

import com.kayo.reminder.application.service.SendDueRemindersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ReminderScheduler {

	private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

	private final SendDueRemindersService reminders;

	public ReminderScheduler(SendDueRemindersService reminders) {
		this.reminders = reminders;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.interval}")
	void tick() {
		try (CorrelationId scope = CorrelationId.open()) {
			try {
				reminders.sendDueReminders();
			} catch (RuntimeException failure) {
				log.error("Reminder run failed: {}", failure.getClass().getSimpleName(), failure);
			}
		}
	}
}
