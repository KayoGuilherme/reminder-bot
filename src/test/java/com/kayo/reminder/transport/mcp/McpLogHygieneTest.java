package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractMcpIntegrationTest;
import com.kayo.reminder.testsupport.LogCapture;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpLogHygieneTest extends AbstractMcpIntegrationTest {

	private static final String WRONG_TOKEN = "wrong-token-value-that-must-not-be-logged";
	private static final String TITLE = "buy the zebra-milk birthday present";

	private static java.util.Set<String> leakingLoggers(LogCapture logs) {
		return logs.events().stream().filter(event -> event.getFormattedMessage().contains("zebra"))
				.map(event -> event.getLoggerName()).collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void titlesArgumentsResultsAndTokensNeverReachTheLogs() throws Exception {
		try (LogCapture logs = LogCapture.ofRootWithConfiguredLibraryLevels()) {
			client.callTool(new CallToolRequest("create_task",
					Map.of("title", TITLE, "deadline", Instant.now().plus(Duration.ofDays(1)).toString())));
			client.callTool(new CallToolRequest("create_task", Map.of("title", TITLE, "deadline", "zebra-tomorrow")));
			client.callTool(new CallToolRequest("complete_task", Map.of("taskId", "zebra-id")));
			client.callTool(new CallToolRequest("list_pending_tasks", Map.of()));
			HttpResponse<String> refused = HttpClient.newHttpClient().send(
					HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
							.header("Authorization", "Bearer " + WRONG_TOKEN).header("Content-Type", "application/json")
							.POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
					HttpResponse.BodyHandlers.ofString());

			String text = logs.allText(event -> !event.getLoggerName().startsWith("com.sun.net.httpserver"));
			assertThat(refused.statusCode()).isEqualTo(401);
			assertThat(text).isNotBlank().as("loggers that wrote the title: %s", leakingLoggers(logs))
					.doesNotContain("zebra").doesNotContain(WRONG_TOKEN).doesNotContain(TOKEN);
		}
	}
}
