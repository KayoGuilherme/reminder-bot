package com.kayo.reminder.infrastructure.llm;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.application.port.out.ParsedTask;
import com.kayo.reminder.application.port.out.TaskParserPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Parses a task with any OpenAI-compatible chat-completions endpoint (Groq, Gemini). The user's text and the model's
 * answer are never logged.
 */
public class ChatCompletionsTaskParser implements TaskParserPort {

	record Completion(List<Choice> choices) {
	}

	record Choice(Message message) {
	}

	record Message(String content) {
	}

	private final RestClient client;
	private final AppProperties.Llm settings;
	private final TaskParserPrompt prompt;
	private final ParsedTaskValidator validator;
	private final SingleRetry retry;

	public ChatCompletionsTaskParser(RestClient client, AppProperties.Llm settings, Clock clock,
			java.time.ZoneId zone, SingleRetry retry) {
		this.client = client;
		this.settings = settings;
		this.prompt = new TaskParserPrompt(clock, zone);
		this.validator = new ParsedTaskValidator(zone);
		this.retry = retry;
	}

	@Override
	public ParsedTask parse(String text) {
		Completion completion = retry.call(() -> post(text));
		if (completion == null || completion.choices() == null || completion.choices().isEmpty()
				|| completion.choices().get(0) == null || completion.choices().get(0).message() == null) {
			throw new ExternalServiceException(Kind.INVALID_RESPONSE, "Task parser");
		}
		return validator.validate(completion.choices().get(0).message().content());
	}

	private Completion post(String text) {
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
				.body(Completion.class);
	}
}
