package com.kayo.reminder.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

class ClockConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(ClockConfig.class);

	@Test
	void clockIsUtc() {
		runner.run(context -> assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC));
	}

	@Test
	void testClockOverridesDefault() {
		Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
		runner.withUserConfiguration(FixedClock.class).run(context ->
				assertThat(context.getBean(Clock.class).instant()).isEqualTo(fixed));
	}

	@Configuration
	static class FixedClock {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		}
	}
}
