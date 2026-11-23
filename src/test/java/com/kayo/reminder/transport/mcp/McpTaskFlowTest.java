package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractMcpIntegrationTest;
import com.kayo.reminder.application.service.TaskService;
import com.kayo.reminder.domain.models.enums.Urgency;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class McpTaskFlowTest extends AbstractMcpIntegrationTest {

	@Autowired
	TaskService tasks;

	private CallToolResult call(String tool, Map<String, Object> arguments) {
		return client.callTool(new CallToolRequest(tool, arguments));
	}

	private static String text(CallToolResult result) {
		return ((TextContent) result.content().get(0)).text();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> structured(CallToolResult result) {
		return (Map<String, Object>) result.structuredContent();
	}

	private Map<String, Object> create(String title, Instant deadline) {
		CallToolResult result = call("create_task", Map.of("title", title, "deadline", deadline.toString(), "urgency", "HIGH"));
		assertThat(result.isError()).as(text(result)).isFalse();
		return structured(result);
	}

	@Test
	void aTaskCreatedOverMcpIsListedAndPickedUpByTheReminderQuery() {
		Instant deadline = Instant.now().plus(Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

		Map<String, Object> created = create("Send the report", deadline);

		assertThat(created.get("status")).isEqualTo("PENDING");
		assertThat(created.get("urgency")).isEqualTo("HIGH");
		assertThat(created.get("deadlineAt")).isEqualTo(deadline.toString());
		assertThat((String) created.get("deadlineLocal")).endsWith("-03:00").matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d-03:00");
		CallToolResult listed = call("list_pending_tasks", Map.of());
		assertThat(structured(listed).get("total")).isEqualTo(1);
		assertThat(text(listed)).contains("Send the report");
		assertThat(tasks.findReminderCandidates(deadline.plusSeconds(60),
				Map.of(Urgency.LOW, 1, Urgency.MEDIUM, 2, Urgency.HIGH, 3))).extracting("title")
				.containsExactly("Send the report");
	}

	@Test
	void aTaskThatWasNeverRemindedCannotBeSnoozed() {
		String id = (String) create("a", Instant.now().plus(Duration.ofHours(2))).get("id");

		CallToolResult result = call("snooze_task", Map.of("taskId", id));

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("NOT_SNOOZABLE: ");
	}

	@Test
	void completingAndThenCancelingFollowsTheFinishRules() {
		String id = (String) create("a", Instant.now().plus(Duration.ofHours(2))).get("id");

		CallToolResult done = call("complete_task", Map.of("taskId", id));
		CallToolResult again = call("complete_task", Map.of("taskId", id));
		CallToolResult canceled = call("cancel_task", Map.of("taskId", id));

		assertThat(structured(done).get("status")).isEqualTo("DONE");
		assertThat(again.isError()).isFalse();
		assertThat(canceled.isError()).isTrue();
		assertThat(text(canceled)).startsWith("TASK_NOT_PENDING: ");
		assertThat(structured(call("list_pending_tasks", Map.of())).get("total")).isEqualTo(0);
	}

	@Test
	void anUnknownTaskIsNotFound() {
		CallToolResult result = call("complete_task", Map.of("taskId", "6f1c1e0e-0000-4000-8000-000000000000"));

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("TASK_NOT_FOUND: ");
	}

	@Test
	void aRefusedCreateIsAnErrorAndSavesNothing() {
		CallToolResult past = call("create_task", Map.of("title", "late", "deadline", Instant.now().minusSeconds(60).toString()));
		CallToolResult unreadable = call("create_task", Map.of("title", "x", "deadline", "tomorrow evening"));

		assertThat(past.isError()).isTrue();
		assertThat(text(past)).startsWith("DEADLINE_NOT_IN_FUTURE: ");
		assertThat(text(unreadable)).startsWith("INVALID_DATETIME: ");
		assertThat(jdbc.queryForObject("select count(*) from tasks", Long.class)).isZero();
	}

	@Test
	void anOutOfRangeLimitIsRefused() {
		CallToolResult result = call("list_pending_tasks", Map.of("limit", 51));

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("INVALID_LIMIT: ");
	}
}
