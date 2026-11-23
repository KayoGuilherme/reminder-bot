package com.kayo.reminder.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class McpPropertiesTest {

	private static final String TOKEN = "0123456789abcdef0123456789abcdef";

	private static final String[] VALID = {
			"TELEGRAM_BOT_TOKEN=bot-token-secret",
			"TELEGRAM_ALLOWED_USER_ID=12345",
			"GROQ_API_KEY=groq-secret",
			"GEMINI_API_KEY=gemini-secret"
	};

	private ApplicationContextRunner runner(String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(Config.class)
				.withPropertyValues(VALID)
				.withPropertyValues(overrides);
	}

	@Test
	void mcpIsDisabledWithoutATokenByDefault() {
		runner().run(context -> {
			McpProperties mcp = context.getBean(AppProperties.class).mcp();
			assertThat(context).hasNotFailed();
			assertThat(mcp.enabled()).isFalse();
		});
	}

	@Test
	void enabledWithAValidTokenBinds() {
		runner("MCP_ENABLED=true", "MCP_TOKEN=" + TOKEN).run(context -> {
			McpProperties mcp = context.getBean(AppProperties.class).mcp();
			assertThat(mcp.enabled()).isTrue();
			assertThat(mcp.token()).isEqualTo(TOKEN);
		});
	}

	@Test
	void enabledWithoutATokenFailsNamingTheSetting() {
		runner("MCP_ENABLED=true").run(context -> assertThat(context)
				.hasFailed()
				.getFailure().hasStackTraceContaining("app.mcp.token"));
	}

	@Test
	void aShortTokenFailsWithoutPrintingIt() {
		runner("MCP_ENABLED=true", "MCP_TOKEN=short-secret").run(context -> {
			assertThat(context).hasFailed();
			Throwable failure = context.getStartupFailure();
			assertThat(failure).hasStackTraceContaining("app.mcp.token");
			assertThat(failure).satisfies(error -> assertThat(stackText(error)).doesNotContain("short-secret"));
		});
	}

	@Test
	void theTokenIsMaskedInThePrintedSettings() {
		runner("MCP_ENABLED=true", "MCP_TOKEN=" + TOKEN).run(context -> {
			AppProperties properties = context.getBean(AppProperties.class);
			assertThat(properties.mcp().toString()).doesNotContain(TOKEN).contains("enabled=true");
			assertThat(properties.toString()).doesNotContain(TOKEN);
		});
	}

	private static String stackText(Throwable error) {
		java.io.StringWriter text = new java.io.StringWriter();
		error.printStackTrace(new java.io.PrintWriter(text));
		return text.toString();
	}

	@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AppProperties.class)
	static class Config {
	}
}
