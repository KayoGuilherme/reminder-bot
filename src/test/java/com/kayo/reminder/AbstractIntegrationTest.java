package com.kayo.reminder;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"TELEGRAM_BOT_TOKEN=test-bot-token-9f8e7d",
				"TELEGRAM_ALLOWED_USER_ID=42",
				"app.telegram.enabled=false",
				"app.scheduler.enabled=false",
				"GROQ_API_KEY=test-groq-key-1a2b3c",
				"GEMINI_API_KEY=test-gemini-key-4d5e6f"
		})
@Import(AbstractIntegrationTest.PostgresConfig.class)
@ExtendWith(OutputCaptureExtension.class)
public abstract class AbstractIntegrationTest {

	@Autowired
	protected JdbcTemplate jdbc;

	@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
	static class PostgresConfig {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgres() {
			return new PostgreSQLContainer("postgres:16-alpine");
		}
	}
}
