package com.kayo.reminder.config;

import com.kayo.reminder.transport.http.RestClients;
import com.kayo.reminder.transport.http.SingleRetry;
import com.kayo.reminder.transport.llm.ChatCompletionsTaskParser;
import com.kayo.reminder.transport.llm.TaskParser;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {

	@Bean
	TaskParser taskParserPort(AppProperties properties, Clock clock) {
		LlmProperties llm = properties.llm();
		return new ChatCompletionsTaskParser(
				RestClients.create(llm.baseUrl(), llm.connectTimeout(), llm.readTimeout()), llm, clock,
				properties.timezone(), new SingleRetry("Task parser", properties.resilience().retryBackoff()));
	}
}
