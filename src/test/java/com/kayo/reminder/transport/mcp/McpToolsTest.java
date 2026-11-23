package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractMcpIntegrationTest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolsTest extends AbstractMcpIntegrationTest {

	private Tool tool(String name) {
		return client.listTools().tools().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow();
	}

	@Test
	void exactlyTheFiveToolsArePublished() {
		List<String> names = client.listTools().tools().stream().map(Tool::name).toList();

		assertThat(names).containsExactlyInAnyOrder("list_pending_tasks", "create_task", "complete_task", "cancel_task",
				"snooze_task");
	}

	@Test
	void everyToolHasADescriptionAndAnInputSchema() {
		client.listTools().tools().forEach(tool -> {
			assertThat(tool.description()).as(tool.name()).isNotBlank();
			assertThat(tool.inputSchema()).as(tool.name()).isNotNull();
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void createTaskRequiresOnlyTheTitleAndTheDeadline() {
		Map<String, Object> schema = tool("create_task").inputSchema();

		assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrder("title", "deadline");
		assertThat(((Map<String, Object>) schema.get("properties")).keySet())
				.containsExactlyInAnyOrder("title", "deadline", "urgency", "startAt", "language");
	}

	@Test
	@SuppressWarnings("unchecked")
	void urgencyIsOfferedAsTheThreeValues() {
		Map<String, Object> properties = (Map<String, Object>) tool("create_task").inputSchema().get("properties");
		Map<String, Object> urgency = (Map<String, Object>) properties.get("urgency");

		assertThat((List<String>) urgency.get("enum")).containsExactlyInAnyOrder("LOW", "MEDIUM", "HIGH");
	}

	@Test
	@SuppressWarnings("unchecked")
	void noToolAsksForAChatOrAUserIdentifier() {
		client.listTools().tools().forEach(tool -> assertThat(((Map<String, Object>) tool.inputSchema().get("properties")).keySet())
				.as(tool.name()).noneMatch(name -> name.toLowerCase().contains("chat") || name.toLowerCase().contains("user")));
	}
}
