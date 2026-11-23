package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class McpDisabledTest extends AbstractIntegrationTest {

	@LocalServerPort
	int port;

	@Test
	void theMcpPathDoesNotExistByDefault() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
						.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
						.POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(404);
	}
}
