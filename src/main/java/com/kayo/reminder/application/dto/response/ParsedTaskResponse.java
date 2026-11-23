package com.kayo.reminder.application.dto.response;

import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.domain.models.enums.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ParsedTaskResponse(
		@NotBlank @Size(max = CreateTaskRequest.MAX_TITLE_LENGTH) String title,
		Urgency urgency,
		Instant startAt,
		Instant deadlineAt,
		String language) {

	public ParsedTaskResponse(String title, Urgency urgency, Instant startAt, Instant deadlineAt) {
		this(title, urgency, startAt, deadlineAt, null);
	}
}
