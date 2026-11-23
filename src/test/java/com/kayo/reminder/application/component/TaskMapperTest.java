package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.TaskEntity;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskMapperTest {

	private static final Instant CREATED = Instant.parse("2026-11-22T13:00:00Z");
	private final TaskMapper mapper = new TaskMapper();

	@Test
	void everyFieldSurvivesTheRoundTrip() {
		TaskResponse task = new TaskResponse(UUID.randomUUID(), 42L, "send report", Urgency.HIGH, TaskStatus.PENDING,
				CREATED.plusSeconds(60), CREATED.plusSeconds(3600), (short) 3, CREATED.plusSeconds(120), CREATED,
				CREATED.plusSeconds(30));

		assertThat(mapper.toModel(mapper.toEntity(task))).isEqualTo(task);
	}

	@Test
	void absentOptionalFieldsStayAbsent() {
		TaskResponse task = new TaskResponse(UUID.randomUUID(), 7L, "pay rent", Urgency.LOW, TaskStatus.DONE, null,
				CREATED.plusSeconds(60), (short) 0, null, CREATED, CREATED);

		assertThat(mapper.toModel(mapper.toEntity(task))).isEqualTo(task);
	}

	@Test
	void theEntityCarriesTheModelValues() {
		TaskResponse task = new TaskResponse(UUID.randomUUID(), 42L, "send report", Urgency.MEDIUM, TaskStatus.CANCELED, null,
				CREATED.plusSeconds(3600), (short) 1, null, CREATED, CREATED);

		TaskEntity entity = mapper.toEntity(task);

		assertThat(entity.id()).isEqualTo(task.id());
		assertThat(entity.chatId()).isEqualTo(42L);
		assertThat(entity.status()).isEqualTo(TaskStatus.CANCELED);
		assertThat(entity.lastReminderStage()).isEqualTo((short) 1);
	}
}
