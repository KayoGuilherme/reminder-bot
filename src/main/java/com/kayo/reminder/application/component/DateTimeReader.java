package com.kayo.reminder.application.component;

import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DateTimeReader {

	private final DateTimeFormatter formatter;

	public DateTimeReader(AppProperties properties) {
		this.formatter = DateTimeFormatter.ISO_DATE_TIME.withZone(properties.timezone());
	}

	public Optional<Instant> read(String text) {
		return Optional.ofNullable(text).map(String::strip).filter(value -> !value.isEmpty()).map(this::parse);
	}

	private Instant parse(String text) {
		try {
			return Instant.from(formatter.parse(text));
		}
		catch (DateTimeException unreadable) {
			throw new TaskActionException(TaskFailure.INVALID_DATETIME);
		}
	}
}
