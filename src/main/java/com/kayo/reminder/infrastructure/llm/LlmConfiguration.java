package com.kayo.reminder.infrastructure.llm;

import com.kayo.reminder.application.port.out.TaskParserPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.RestClients;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {

	@Bean
	TaskParserPort taskParserPort(AppProperties properties, Clock clock) {
		AppProperties.Llm llm = properties.llm();
		return new ChatCompletionsTaskParser(
				RestClients.create(llm.baseUrl(), llm.connectTimeout(), llm.readTimeout()), llm, clock,
				properties.timezone(), new SingleRetry("Task parser", properties.resilience().retryBackoff()));
	}
}
