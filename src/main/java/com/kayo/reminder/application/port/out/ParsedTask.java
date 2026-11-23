package com.kayo.reminder.application.port.out;

import com.kayo.reminder.domain.Urgency;
import java.time.Instant;

/**
 * What the parser understood from a message. Only the title is guaranteed; everything else may be absent.
 * The deadline is not checked against the clock here.
 */
public record ParsedTask(String title, Urgency urgency, Instant startAt, Instant deadlineAt) {
}
