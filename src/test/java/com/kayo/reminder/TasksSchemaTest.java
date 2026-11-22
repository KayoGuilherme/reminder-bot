package com.kayo.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TasksSchemaTest extends AbstractIntegrationTest {

	@Test
	void insertWithOnlyRequiredColumnsAppliesDefaults() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into tasks (id, chat_id, title, urgency, deadline_at) values (?, 1, 't', 'LOW', now())", id);
		Map<String, Object> row = jdbc.queryForMap("select * from tasks where id = ?", id);
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(((Number) row.get("last_reminder_stage")).intValue()).isZero();
		assertThat(row.get("start_at")).isNull();
		assertThat(row.get("last_reminded_at")).isNull();
		assertThat(row.get("created_at")).isNotNull();
		assertThat(row.get("updated_at")).isNotNull();
	}

	@Test
	void rejectsInvalidUrgency() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into tasks (id, chat_id, title, urgency, deadline_at) values (?, 1, 't', 'URGENT', now())",
				UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsInvalidStatus() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into tasks (id, chat_id, title, urgency, deadline_at) values (?, 1, 't', 'LOW', now())", id);
		assertThatThrownBy(() -> jdbc.update("update tasks set status = 'ARCHIVED' where id = ?", id))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsMissingDeadlineAndTitle() {
		assertThatThrownBy(() -> jdbc.update(
				"insert into tasks (id, chat_id, title, urgency) values (?, 1, 't', 'LOW')", UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(
				"insert into tasks (id, chat_id, urgency, deadline_at) values (?, 1, 'LOW', now())", UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
