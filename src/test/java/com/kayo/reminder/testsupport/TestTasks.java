package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class TestTasks {

	private TestTasks() {
	}

	public static TaskResponse create(UUID id, long chatId, String title, Urgency urgency, Instant startAt,
			Instant deadlineAt, Instant now) {
		return new TaskFactory(Clock.fixed(now, ZoneOffset.UTC)).create(new CreateTaskRequest(id, chatId, title, urgency, startAt, deadlineAt));
	}
}
