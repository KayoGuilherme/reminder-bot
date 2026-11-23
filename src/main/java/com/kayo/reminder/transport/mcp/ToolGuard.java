package com.kayo.reminder.transport.mcp;

import com.kayo.reminder.application.component.CorrelationId;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.exceptions.TaskValidationException;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

public class ToolGuard {

	public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

	private static final String INTERNAL_SENTENCE = "The request could not be completed. Try again later.";

	private static final Logger log = LoggerFactory.getLogger(ToolGuard.class);

	private final JsonMapper mapper;

	public ToolGuard(JsonMapper mapper) {
		this.mapper = mapper;
	}

	public CallToolResult run(String tool, Supplier<Object> action) {
		try (CorrelationId correlation = CorrelationId.open()) {
			return success(tool, action.get());
		}
		catch (TaskActionException refused) {
			return refusal(tool, refused.failure().name(), refused.failure().sentence());
		}
		catch (TaskValidationException invalid) {
			return refusal(tool, invalid.rule().name(), invalid.rule().sentence());
		}
		catch (RuntimeException failure) {
			log.warn("Tool {} failed: {}", tool, failure.getClass().getSimpleName());
			return refusal(tool, INTERNAL_ERROR, INTERNAL_SENTENCE);
		}
	}

	private CallToolResult success(String tool, Object result) {
		log.info("Tool {} succeeded", tool);
		return CallToolResult.builder().addTextContent(mapper.writeValueAsString(result)).structuredContent(result)
				.isError(false).build();
	}

	private CallToolResult refusal(String tool, String reason, String sentence) {
		log.info("Tool {} refused with {}", tool, reason);
		return CallToolResult.builder().addTextContent(reason + ": " + sentence).isError(true).build();
	}
}
