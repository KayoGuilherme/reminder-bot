package com.kayo.reminder.transport.llm;

import com.kayo.reminder.application.dto.response.ParsedTaskResponse;
import com.kayo.reminder.config.LlmProperties;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.transport.http.SingleRetry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class ChatCompletionsTaskParser implements TaskParser {

	private final RestClient client;
	private final LlmProperties settings;
	private final TaskParserPrompt prompt;
	private final ParsedTaskValidator validator;
	private final SingleRetry retry;

	public ChatCompletionsTaskParser(RestClient client, LlmProperties settings, Clock clock,
			java.time.ZoneId zone, SingleRetry retry) {
		this.client = client;
		this.settings = settings;
		this.prompt = new TaskParserPrompt(clock, zone);
		this.validator = new ParsedTaskValidator(zone);
		this.retry = retry;
	}

	@Override
	public ParsedTaskResponse parse(String text) {
		ChatCompletion completion = retry.call(() -> post(text));
		if (completion == null || completion.choices() == null || completion.choices().isEmpty()
				|| completion.choices().get(0) == null || completion.choices().get(0).message() == null) {
			throw new ExternalServiceException(ExternalFailureKind.INVALID_RESPONSE, "Task parser");
		}
		return validator.validate(completion.choices().get(0).message().content());
	}

	private ChatCompletion post(String text) {
		Map<String, Object> body = Map.of(
				"model", settings.model(),
				"temperature", 0,
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(
						Map.of("role", "system", "content", prompt.system()),
						Map.of("role", "user", "content", text)));
		return client.post().uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + settings.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(ChatCompletion.class);
	}
}
