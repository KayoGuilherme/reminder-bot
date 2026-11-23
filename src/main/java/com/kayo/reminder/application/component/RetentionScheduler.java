package com.kayo.reminder.application.component;

import com.kayo.reminder.application.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class RetentionScheduler {

	static final String CRON = "0 0 3 * * *";

	private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

	private final RetentionService retention;

	public RetentionScheduler(RetentionService retention) {
		this.retention = retention;
	}

	@Scheduled(cron = CRON, zone = "${app.timezone}")
	void run() {
		try (CorrelationId scope = CorrelationId.open()) {
			try {
				int deleted = retention.deleteExpired();
				log.info("Retention cleanup deleted {} finished tasks", deleted);
			}
			catch (RuntimeException failure) {
				log.error("Retention cleanup failed: {}", failure.getClass().getSimpleName(), failure);
			}
		}
	}
}
