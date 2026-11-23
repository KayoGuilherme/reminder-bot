package com.kayo.reminder.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Booting this context with {@code ddl-auto=validate} also proves the mapping matches V1 (no new migration). */
class JpaTaskRepositoryTest extends AbstractIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final Instant DEADLINE = Instant.parse("2026-11-22T21:00:00Z");

	@Autowired
	TaskRepository repository;

	private static Task task(UUID id, Instant start) {
		return Task.create(id, 42L, "send report", Urgency.HIGH, start, DEADLINE, NOW);
	}

	@Test
	void savesAPendingTaskAsOneRow() {
		UUID id = UUID.randomUUID();
		repository.save(task(id, null));

		Map<String, Object> row = jdbc.queryForMap("select * from tasks where id = ?", id);
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("urgency")).isEqualTo("HIGH");
		assertThat(((Number) row.get("chat_id")).longValue()).isEqualTo(42L);
		assertThat(((Number) row.get("last_reminder_stage")).intValue()).isZero();
		assertThat(row.get("start_at")).isNull();
		assertThat(Instant.ofEpochMilli(((java.util.Date) row.get("deadline_at")).getTime())).isEqualTo(DEADLINE);
		assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void roundTripsEveryField() {
		UUID id = UUID.randomUUID();
		Task saved = task(id, NOW.plusSeconds(60));
		repository.save(saved);

		Task loaded = repository.findById(id).orElseThrow();
		assertThat(loaded).isEqualTo(saved);
		assertThat(loaded.status()).isEqualTo(TaskStatus.PENDING);
		assertThat(loaded.startAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void unknownIdIsEmpty() {
		assertThat(repository.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void savingTheSameIdTwiceFailsAndKeepsTheFirstRow() {
		UUID id = UUID.randomUUID();
		repository.save(task(id, null));

		Task other = Task.create(id, 99L, "different", Urgency.LOW, null, DEADLINE.plusSeconds(60), NOW);
		assertThatThrownBy(() -> repository.save(other)).isInstanceOf(RuntimeException.class);

		assertThat(jdbc.queryForObject("select count(*) from tasks where id = ?", Integer.class, id)).isEqualTo(1);
		assertThat(repository.findById(id).orElseThrow().title()).isEqualTo("send report");
	}
}
