package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.service.RetentionService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

class RetentionIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	RetentionService retention;

	@BeforeEach
	void clean() {
		jdbc.update("delete from tasks");
	}

	private UUID insert(String status, Duration age) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into tasks (id, chat_id, title, urgency, status, deadline_at, updated_at)
				values (?, 1, 't', 'LOW', ?, now(), ?)
				""", id, status, Timestamp.from(Instant.now().minus(age)));
		return id;
	}

	private boolean exists(UUID id) {
		return jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id) == 1;
	}

	@Test
	void theDailyJobIsRegisteredByDefault() {
		assertThat(context.getBeansOfType(RetentionScheduler.class)).hasSize(1);
	}

	@Test
	void oldFinishedTasksAreRemovedAndEverythingElseStays() {
		UUID oldDone = insert("DONE", Duration.ofDays(31));
		UUID oldCanceled = insert("CANCELED", Duration.ofDays(40));
		UUID recentDone = insert("DONE", Duration.ofDays(29));
		UUID oldPending = insert("PENDING", Duration.ofDays(200));

		assertThat(retention.deleteExpired()).isEqualTo(2);

		assertThat(exists(oldDone)).isFalse();
		assertThat(exists(oldCanceled)).isFalse();
		assertThat(exists(recentDone)).isTrue();
		assertThat(exists(oldPending)).isTrue();
	}
}
