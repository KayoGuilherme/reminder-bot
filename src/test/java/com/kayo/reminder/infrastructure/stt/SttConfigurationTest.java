package com.kayo.reminder.infrastructure.stt;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.out.TranscriptionPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.testsupport.FakeHttpServer;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** The retry pause of the real bean comes from {@code app.resilience.retry-backoff}. */
class SttConfigurationTest {

	@Test
	void aTransientFailureIsRetriedOnceAfterTheConfiguredBackoff() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer()) {
			server.reply(503, "{\"error\":\"down\"}").reply(200, "{\"text\":\"hello\"}");
			AppProperties base = TestProperties.of(ZoneId.of("UTC"), "en");
			AppProperties properties = new AppProperties(base.timezone(), base.language(), base.telegram(),
					base.groq(), base.gemini(),
					new AppProperties.Stt("pt", "m", server.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(5)),
					base.llm(), base.voice(), base.draft(), base.scheduler(), base.reminder(), base.retention(),
					new AppProperties.Resilience(Duration.ofMillis(300)), base.shutdown());
			TranscriptionPort port = new SttConfiguration().transcriptionPort(properties);

			long start = System.nanoTime();
			String text = port.transcribe(new byte[] { 1, 2, 3 });
			Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

			assertThat(text).isEqualTo("hello");
			assertThat(server.requests()).hasSize(2);
			assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(290));
		}
	}
}
