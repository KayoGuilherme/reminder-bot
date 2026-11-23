package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractMcpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

class McpStartupLogTest extends AbstractMcpIntegrationTest {

	@Test
	void theTokenIsNotWrittenToTheStartupLog(CapturedOutput output) {
		assertThat(output.getAll()).isNotBlank().doesNotContain(TOKEN);
	}
}
