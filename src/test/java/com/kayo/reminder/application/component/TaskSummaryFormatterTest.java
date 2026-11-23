package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.testsupport.TestMessages;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TaskSummaryFormatterTest {

	private static final Instant NOON_UTC = Instant.parse("2026-11-23T12:00:00Z");
	private static final TaskSummaryFormatter FORMATTER = new TaskSummaryFormatter(ZoneId.of("America/Fortaleza"));

	@Test
	void aPortugueseDateUsesTheBrazilianPatternInTheDisplayZone() {
		assertThat(FORMATTER.format(TestMessages.of("pt-BR"), NOON_UTC)).isEqualTo("23/11/2026 09:00");
	}

	@Test
	void anEnglishDateUsesTheIsoPatternInTheDisplayZone() {
		assertThat(FORMATTER.format(TestMessages.of("en"), NOON_UTC)).isEqualTo("2026-11-23 09:00");
	}
}
