package com.kayo.reminder.application.port.out;

import com.kayo.reminder.domain.Urgency;
import java.time.Instant;

/**
 * What the parser understood from a message. Only the title is guaranteed; everything else may be absent.
 * The deadline is not checked against the clock here.
 *
 * @param language the language the message was written in, as {@code "pt-BR"} or {@code "en"}; null when unknown or
 *                 another language, in which case the configured language is used for the replies
 */
public record ParsedTask(String title, Urgency urgency, Instant startAt, Instant deadlineAt, String language) {

	public ParsedTask(String title, Urgency urgency, Instant startAt, Instant deadlineAt) {
		this(title, urgency, startAt, deadlineAt, null);
	}
}
