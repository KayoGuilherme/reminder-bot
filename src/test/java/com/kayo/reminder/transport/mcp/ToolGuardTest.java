package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.response.PendingTasksResponse;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.exceptions.TaskValidationException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskRule;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ToolGuardTest {

	private final ToolGuard guard = new ToolGuard(JsonMapper.builder().build());

	private static String text(CallToolResult result) {
		return ((TextContent) result.content().get(0)).text();
	}

	@Test
	void aSuccessCarriesTheResultAsJson() {
		CallToolResult result = guard.run("list_pending_tasks", () -> new PendingTasksResponse(List.of(), 0));

		assertThat(result.isError()).isFalse();
		assertThat(text(result)).contains("\"total\":0");
		assertThat(result.structuredContent()).isNotNull();
	}

	@Test
	void aTaskFailureIsAnErrorStartingWithItsCode() {
		CallToolResult result = guard.run("complete_task", () -> {
			throw new TaskActionException(TaskFailure.TASK_NOT_FOUND);
		});

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("TASK_NOT_FOUND: ").contains(TaskFailure.TASK_NOT_FOUND.sentence());
	}

	@Test
	void aBrokenTaskRuleIsAnErrorStartingWithTheRuleName() {
		CallToolResult result = guard.run("create_task", () -> {
			throw new TaskValidationException(TaskRule.DEADLINE_NOT_IN_FUTURE);
		});

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("DEADLINE_NOT_IN_FUTURE: ");
	}

	@Test
	void anUnexpectedFailureIsAnInternalErrorWithoutDetail() {
		CallToolResult result = guard.run("create_task", () -> {
			throw new IllegalStateException("could not connect to jdbc:postgresql://db:5432/secret");
		});

		assertThat(result.isError()).isTrue();
		assertThat(text(result)).startsWith("INTERNAL_ERROR: ").doesNotContain("jdbc").doesNotContain("secret")
				.doesNotContain("IllegalStateException").doesNotContain("\tat ").doesNotContain("com.kayo");
	}
}
