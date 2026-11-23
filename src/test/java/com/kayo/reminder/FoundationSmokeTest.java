package com.kayo.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;

class FoundationSmokeTest extends AbstractIntegrationTest {

	@LocalServerPort
	int port;

	@Test
	void flywayAppliedV1() {
		Boolean success = jdbc.queryForObject(
				"select success from flyway_schema_history where version = '1'", Boolean.class);
		assertThat(success).isTrue();
	}

	@Test
	void tasksTableHasExpectedColumnsAndIndex() {
		List<String> columns = jdbc.queryForList(
				"select column_name from information_schema.columns where table_name = 'tasks'", String.class);
		assertThat(columns).containsExactlyInAnyOrder("id", "chat_id", "title", "urgency", "status", "start_at",
				"deadline_at", "last_reminder_stage", "last_reminded_at", "created_at", "updated_at", "language");
		String indexDef = jdbc.queryForObject(
				"select indexdef from pg_indexes where tablename = 'tasks' and indexname = 'idx_tasks_status_deadline'",
				String.class);
		assertThat(indexDef).contains("(status, deadline_at)");
	}

	@Test
	void healthAndProbesAreUp() throws Exception {
		assertThat(get("/actuator/health").body()).contains("\"UP\"");
		assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
		assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
	}

	@Test
	void otherActuatorEndpointsAreHidden() throws Exception {
		assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
		assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
	}

	@Test
	void secretsNotLogged(CapturedOutput output) {
		assertThat(output.getAll()).doesNotContain("test-bot-token-9f8e7d", "test-groq-key-1a2b3c",
				"test-gemini-key-4d5e6f");
	}

	private HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
				HttpResponse.BodyHandlers.ofString());
	}
}
