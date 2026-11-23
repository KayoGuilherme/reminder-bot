package com.kayo.reminder.application.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Renders a draft as the lines shown to the owner, with times in the display timezone. */
class TaskSummaryFormatter {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final Messages messages;
	private final ZoneId zone;

	TaskSummaryFormatter(Messages messages, ZoneId zone) {
		this.messages = messages;
		this.zone = zone;
	}

	String summary(Draft draft) {
		String urgency = messages.get("task.urgency." + draft.urgency().name());
		if (draft.urgencyAssumed()) {
			urgency = messages.get("task.urgency-assumed", urgency);
		}
		StringBuilder text = new StringBuilder()
				.append(messages.get("task.summary.title", draft.title())).append('\n')
				.append(messages.get("task.summary.urgency", urgency)).append('\n')
				.append(messages.get("task.summary.deadline", format(draft.deadlineAt())));
		if (draft.startAt() != null) {
			text.append('\n').append(messages.get("task.summary.start", format(draft.startAt())));
		}
		return text.toString();
	}

	private String format(Instant instant) {
		return TIME.format(instant.atZone(zone));
	}
}
