package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.port.in.CreateTaskFromVoiceUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.application.port.out.ParsedTask;
import com.kayo.reminder.application.port.out.TaskRepository;
import com.kayo.reminder.domain.Urgency;
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

/** The acceptance scenario against a real PostgreSQL, with the speech and language services faked. */
class VoiceToTaskIntegrationTest extends AbstractIntegrationTest {

	private static final long CHAT = 4242L;
	/** 10:00 in Fortaleza on 2026-11-22. */
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");

	@Autowired
	TaskRepository repository;

	private final RecordingSender sender = new RecordingSender();
	private CreateTaskFromVoiceUseCase flow;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from tasks");
		MutableClock clock = new MutableClock(NOW);
		flow = new CreateTaskFromVoiceService(sender, fileId -> new byte[] { 1 },
				audio -> "preciso enviar o relatório até as 18h, é urgente",
				text -> new ParsedTask("Enviar o relatório", Urgency.HIGH, null, Instant.parse("2026-11-22T21:00:00Z")),
				repository, new DraftStore(clock, Duration.ofMinutes(10)), TestMessages.of("en"),
				TestProperties.of(ZoneId.of("America/Fortaleza"), "en"), clock);
	}

	private int taskCount() {
		return jdbc.queryForObject("select count(*) from tasks", Integer.class);
	}

	@Test
	void voiceNoteThenConfirmCreatesExactlyOnePendingTask() {
		flow.submit(IncomingCommand.voice(CHAT, 42, 7, "file-1", 6));

		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("Enviar o relatório").contains("High")
				.contains("2026-11-22 18:00");
		assertThat(taskCount()).isZero();

		String data = sender.withButtons.get(0).buttons().get(0).get(0).callbackData();
		UUID id = UUID.fromString(data.substring(CreateTaskFromVoiceUseCase.CONFIRM_PREFIX.length()));
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

		flow.confirm(CHAT, 9, id); // second press
		assertThat(taskCount()).isEqualTo(1);
		assertThat(sender.texts).hasSize(1);
	}

	@Test
	void cancelLeavesTheTableEmpty() {
		flow.submit(IncomingCommand.text(CHAT, 42, 7, "enviar relatório até as 18h"));
		String data = sender.withButtons.get(0).buttons().get(0).get(1).callbackData();
		flow.cancel(CHAT, 9, UUID.fromString(data.substring(CreateTaskFromVoiceUseCase.CANCEL_PREFIX.length())));
		assertThat(taskCount()).isZero();
	}
}
