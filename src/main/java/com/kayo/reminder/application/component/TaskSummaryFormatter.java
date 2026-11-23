package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.response.DraftResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TaskSummaryFormatter {

	private final ZoneId zone;

	public TaskSummaryFormatter(ZoneId zone) {
		this.zone = zone;
	}

	public String summary(Messages messages, DraftResponse draft) {
		String urgency = messages.get("task.urgency." + draft.urgency().name());
		if (draft.urgencyAssumed()) {
			urgency = messages.get("task.urgency-assumed", urgency);
		}
		StringBuilder text = new StringBuilder()
				.append(messages.get("task.summary.title", draft.title())).append('\n')
				.append(messages.get("task.summary.urgency", urgency,
						messages.get("tasks.marker." + draft.urgency().name()))).append('\n')
				.append(messages.get("task.summary.deadline", format(messages, draft.deadlineAt())));
		if (draft.startAt() != null) {
			text.append('\n').append(messages.get("task.summary.start", format(messages, draft.startAt())));
		}
		return text.toString();
	}

	public String listItem(Messages messages, TaskResponse task, Instant now) {
		String head = messages.get("tasks.list.item", messages.get("tasks.marker." + task.urgency().name()),
				messages.get("task.urgency." + task.urgency().name()), task.title());
		String due = task.deadlineAt().isAfter(now) ? messages.get("tasks.list.due", format(messages, task.deadlineAt()))
				: messages.get("tasks.list.overdue", format(messages, task.deadlineAt()));
		return head + '\n' + due;
	}

	public String format(Messages messages, Instant instant) {
		return DateTimeFormatter.ofPattern(messages.get("date.pattern")).format(instant.atZone(zone));
	}
}
