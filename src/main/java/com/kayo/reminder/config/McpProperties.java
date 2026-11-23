package com.kayo.reminder.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
public class McpProperties {

	public static final int MINIMUM_TOKEN_LENGTH = 32;

	private final boolean enabled;

	private final String token;

	public McpProperties(@DefaultValue("false") boolean enabled, @DefaultValue("") String token) {
		this.enabled = enabled;
		this.token = token;
	}

	@AssertTrue(message = "app.mcp.token (MCP_TOKEN) must be at least 32 characters when app.mcp.enabled (MCP_ENABLED) is true")
	public boolean isTokenValidWhenEnabled() {
		return !enabled || token != null && token.length() >= MINIMUM_TOKEN_LENGTH;
	}

	@Override
	public String toString() {
		return "McpProperties[enabled=" + enabled + ", token=***]";
	}
}
