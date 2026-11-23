package com.kayo.reminder.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.DraftResponse;
import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.application.dto.response.ReminderDecisionResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.domain.models.enums.SendFailureReason;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.TestTasks;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DtoValueEqualityTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final Instant NOW = Instant.parse("2026-11-22T13:00:00Z");
	private static final Instant LATER = NOW.plusSeconds(3600);

	private static void assertValueEquality(Object first, Object same, Object different) {
		assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(different);
	}

	@Test
	void tasksWithEqualFieldsAreEqual() {
		assertValueEquality(TestTasks.create(ID, 7L, "a", Urgency.LOW, null, LATER, NOW),
				TestTasks.create(ID, 7L, "a", Urgency.LOW, null, LATER, NOW),
				TestTasks.create(ID, 7L, "b", Urgency.LOW, null, LATER, NOW));
	}

	@Test
	void reminderDecisionsWithEqualFieldsAreEqual() {
		assertValueEquality(ReminderDecisionResponse.overdue(4, 1), ReminderDecisionResponse.overdue(4, 1),
				ReminderDecisionResponse.overdue(5, 2));
	}

	@Test
	void incomingCommandsWithEqualFieldsAreEqual() {
		assertValueEquality(IncomingCommandRequest.text(1, 2, 3, "a"), IncomingCommandRequest.text(1, 2, 3, "a"),
				IncomingCommandRequest.text(1, 2, 3, "b"));
	}

	@Test
	void buttonsWithEqualFieldsAreEqual() {
		assertValueEquality(new ButtonResponse("Done", "task:done:1"), new ButtonResponse("Done", "task:done:1"),
				new ButtonResponse("Done", "task:done:2"));
	}

	@Test
	void sendResultsWithEqualFieldsAreEqual() {
		assertValueEquality(SendResultResponse.ok(5), SendResultResponse.ok(5), SendResultResponse.failed(SendFailureReason.TRANSIENT));
	}

	@Test
	void parsedTasksWithEqualFieldsAreEqual() {
		assertValueEquality(new ParsedTaskResponse("a", Urgency.LOW, null, LATER, "en"),
				new ParsedTaskResponse("a", Urgency.LOW, null, LATER, "en"), new ParsedTaskResponse("a", Urgency.LOW, null, LATER));
	}

	@Test
	void draftsWithEqualFieldsAreEqual() {
		assertValueEquality(new DraftResponse(ID, 1, "a", Urgency.LOW, false, null, LATER, LATER, "en"),
				new DraftResponse(ID, 1, "a", Urgency.LOW, false, null, LATER, LATER, "en"),
				new DraftResponse(ID, 1, "b", Urgency.LOW, false, null, LATER, LATER, "en"));
	}
}
