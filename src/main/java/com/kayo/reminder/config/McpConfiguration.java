package com.kayo.reminder.config;

import com.kayo.reminder.application.service.CreateTaskService;
import com.kayo.reminder.application.service.FinishTaskService;
import com.kayo.reminder.application.service.ListPendingTasksService;
import com.kayo.reminder.application.service.SnoozeTaskService;
import com.kayo.reminder.transport.mcp.McpTokenFilter;
import com.kayo.reminder.transport.mcp.TaskTools;
import com.kayo.reminder.transport.mcp.ToolGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class McpConfiguration {

	private static final String ENDPOINT = "/mcp";

	@Bean
	FilterRegistrationBean<McpTokenFilter> mcpTokenFilter(AppProperties properties) {
		FilterRegistrationBean<McpTokenFilter> registration = new FilterRegistrationBean<>(
				new McpTokenFilter(properties.mcp().token()));
		registration.addUrlPatterns(ENDPOINT, ENDPOINT + "/*");
		return registration;
	}

	@Bean
	ToolGuard toolGuard(JsonMapper mapper) {
		return new ToolGuard(mapper);
	}

	@Bean
	TaskTools taskTools(ToolGuard guard, ListPendingTasksService list, CreateTaskService create, FinishTaskService finish,
			SnoozeTaskService snooze) {
		return new TaskTools(guard, list, create, finish, snooze);
	}
}
