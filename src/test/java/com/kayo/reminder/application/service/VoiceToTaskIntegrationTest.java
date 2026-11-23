package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.component.DraftStore;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VoiceToTaskIntegrationTest extends AbstractIntegrationTest {

	private static final long CHAT = 4242L;
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");

	@Autowired
	TaskService repository;

	private final RecordingSender sender = new RecordingSender();
	private CreateTaskFromVoiceService flow;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from tasks");
		MutableClock clock = new MutableClock(NOW);
		flow = new CreateTaskFromVoiceService(sender, fileId -> new byte[] { 1 },
				audio -> "preciso enviar o relatório até as 18h, é urgente",
				text -> new ParsedTaskResponse("Enviar o relatório", Urgency.HIGH, null, Instant.parse("2026-11-22T21:00:00Z")),
				repository, new DraftStore(clock, Duration.ofMinutes(10)), TestMessages.of("en"),
				TestProperties.of(ZoneId.of("America/Fortaleza"), "en"), clock, new TaskFactory(clock));
	}

	private int taskCount() {
		return jdbc.queryForObject("select count(*) from tasks", Integer.class);
	}

	@Test
	void voiceNoteThenConfirmCreatesExactlyOnePendingTask() {
		flow.submit(IncomingCommandRequest.voice(CHAT, 42, 7, "file-1", 6));

		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("Enviar o relatório").contains("High")
				.contains("2026-11-22 18:00");
		assertThat(taskCount()).isZero();

		String data = sender.withButtons.get(0).buttons().get(0).get(0).callbackData();
		UUID id = UUID.fromString(data.substring(CreateTaskFromVoiceService.CONFIRM_PREFIX.length()));
		flow.confirm(CHAT, 9, id);

		List<Map<String, Object>> rows = jdbc.queryForList("select * from tasks");
		assertThat(rows).hasSize(1);
		Map<String, Object> row = rows.get(0);
		assertThat(row.get("id")).isEqualTo(id);
		assertThat(row.get("status")).isEqualTo("PENDING");
		assertThat(row.get("urgency")).isEqualTo("HIGH");
		assertThat(row.get("title")).isEqualTo("Enviar o relatório");
		assertThat(((Number) row.get("chat_id")).longValue()).isEqualTo(CHAT);
		assertThat(((Timestamp) row.get("deadline_at")).toInstant()).isEqualTo(Instant.parse("2026-11-22T21:00:00Z"));

		flow.confirm(CHAT, 9, id);
		assertThat(taskCount()).isEqualTo(1);
		assertThat(sender.texts).hasSize(1);
	}

	@Test
	void cancelLeavesTheTableEmpty() {
		flow.submit(IncomingCommandRequest.text(CHAT, 42, 7, "enviar relatório até as 18h"));
		String data = sender.withButtons.get(0).buttons().get(0).get(1).callbackData();
		flow.cancel(CHAT, 9, UUID.fromString(data.substring(CreateTaskFromVoiceService.CANCEL_PREFIX.length())));
		assertThat(taskCount()).isZero();
	}
}
