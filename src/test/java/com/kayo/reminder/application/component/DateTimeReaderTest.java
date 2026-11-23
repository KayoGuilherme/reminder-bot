package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateTimeReaderTest {

	private final DateTimeReader reader = new DateTimeReader(TestProperties.of(ZoneId.of("America/Fortaleza"), "en"));

	@Test
	void aDateTimeWithoutAnOffsetIsReadInTheDisplayTimezone() {
		assertThat(reader.read("2026-11-25T18:00:00")).contains(Instant.parse("2026-11-25T21:00:00Z"));
	}

	@Test
	void secondsAreOptional() {
		assertThat(reader.read("2026-11-25T18:00")).contains(Instant.parse("2026-11-25T21:00:00Z"));
	}

	@Test
	void anExplicitOffsetIsKept() {
		assertThat(reader.read("2026-11-25T18:00:00+02:00")).contains(Instant.parse("2026-11-25T16:00:00Z"));
	}

	@Test
	void zuluIsKept() {
		assertThat(reader.read("2026-11-25T18:00:00Z")).contains(Instant.parse("2026-11-25T18:00:00Z"));
	}

	@Test
	void absentOrBlankTextReadsAsNothing() {
		assertThat(reader.read(null)).isEmpty();
		assertThat(reader.read("   ")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-25", "tomorrow evening", "18:00", "2026-13-45T10:00:00" })
	void unreadableTextIsRefused(String text) {
		assertThatThrownBy(() -> reader.read(text)).isInstanceOf(TaskActionException.class)
				.extracting(error -> ((TaskActionException) error).failure()).isEqualTo(TaskFailure.INVALID_DATETIME);
	}
}
