package com.kayo.reminder.application.service;

import com.kayo.reminder.domain.Task;
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

	/** One entry of the /tasks list: marker, urgency and title, then the deadline (flagged when already passed). */
	String listItem(Task task, Instant now) {
		String head = messages.get("tasks.list.item", messages.get("tasks.marker." + task.urgency().name()),
				messages.get("task.urgency." + task.urgency().name()), task.title());
		String due = task.deadlineAt().isAfter(now) ? messages.get("tasks.list.due", format(task.deadlineAt()))
				: messages.get("tasks.list.overdue", format(task.deadlineAt()));
		return head + '\n' + due;
	}

	String format(Instant instant) {
		return TIME.format(instant.atZone(zone));
	}
}
