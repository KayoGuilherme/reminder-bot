package com.kayo.reminder.infrastructure.scheduling;

import com.kayo.reminder.application.port.in.CleanUpFinishedTasksUseCase;
import com.kayo.reminder.infrastructure.logging.CorrelationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the cleanup once a day at 03:00 in the display timezone. A fixed time of day (not a delay) means a restart
 * later the same day does not clean again, and a failed run is simply tried again the next day.
 */
class RetentionScheduler {

	/** Seconds, minutes, hours, day, month, weekday: every day at 03:00. */
	static final String CRON = "0 0 3 * * *";

	private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

	private final CleanUpFinishedTasksUseCase useCase;

	RetentionScheduler(CleanUpFinishedTasksUseCase useCase) {
		this.useCase = useCase;
	}

	@Scheduled(cron = CRON, zone = "${app.timezone}")
	void run() {
		try (CorrelationId scope = CorrelationId.open()) {
			try {
				int deleted = useCase.deleteExpired();
				log.info("Retention cleanup deleted {} finished tasks", deleted);
			}
			catch (RuntimeException e) {
				log.error("Retention cleanup failed: {}", e.getClass().getSimpleName(), e);
			}
		}
	}
}
