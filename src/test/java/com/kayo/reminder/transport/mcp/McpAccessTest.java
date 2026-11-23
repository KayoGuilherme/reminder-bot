package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractMcpIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class McpAccessTest extends AbstractMcpIntegrationTest {

	private static final String INITIALIZE = """
			{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
			"capabilities":{},"clientInfo":{"name":"access-test","version":"1"}}}""";

	@Test
	void aRequestWithoutTheTokenIsRefusedBeforeAnyTool() throws Exception {
		HttpResponse<String> response = post(null);

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
	}

	@Test
	void aWrongTokenIsRefused() throws Exception {
		assertThat(post("Bearer " + TOKEN + "0").statusCode()).isEqualTo(401);
	}

	@Test
	void theValidTokenOpensASession() throws Exception {
		HttpResponse<String> response = post("Bearer " + TOKEN);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("reminder-bot");
	}

	@Test
	void healthStaysOpenAndOtherManagementEndpointsStayHidden() throws Exception {
		assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
		assertThat(get("/actuator/health").body()).contains("\"UP\"");
		assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
		assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
	}

	private HttpResponse<String> post(String authorization) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
				.header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
				.POST(HttpRequest.BodyPublishers.ofString(INITIALIZE));
		Optional.ofNullable(authorization).ifPresent(value -> request.header("Authorization", value));
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
				HttpResponse.BodyHandlers.ofString());
	}
}
