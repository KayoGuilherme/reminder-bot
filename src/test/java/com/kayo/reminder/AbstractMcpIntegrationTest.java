package com.kayo.reminder;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
		"MCP_ENABLED=true",
		"MCP_TOKEN=" + AbstractMcpIntegrationTest.TOKEN
})
public abstract class AbstractMcpIntegrationTest extends AbstractIntegrationTest {

	public static final String TOKEN = "mcp-test-token-0123456789abcdef-0123456789";

	@LocalServerPort
	protected int port;

	protected McpSyncClient client;

	@BeforeEach
	void connect() {
		jdbc.update("delete from tasks");
		client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp")
				.requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + TOKEN)).build()).build();
		client.initialize();
	}

	@AfterEach
	void disconnect() {
		client.closeGracefully();
	}
}
