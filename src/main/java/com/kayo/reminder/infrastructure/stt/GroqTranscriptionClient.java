package com.kayo.reminder.infrastructure.stt;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.application.port.out.TranscriptionPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Speech-to-text through Groq's OpenAI-compatible {@code /audio/transcriptions}. The audio and the transcript are
 * only ever held in memory and never logged.
 */
public class GroqTranscriptionClient implements TranscriptionPort {

	private static final String SERVICE = "Transcription";

	record Response(String text) {
	}

	private final RestClient client;
	private final String apiKey;
	private final AppProperties.Stt settings;
	private final SingleRetry retry;

	public GroqTranscriptionClient(RestClient client, String apiKey, AppProperties.Stt settings, SingleRetry retry) {
		this.client = client;
		this.apiKey = apiKey;
		this.settings = settings;
		this.retry = retry;
	}

	@Override
	public String transcribe(byte[] audio) {
		Response response = retry.call(() -> post(audio));
		String text = response == null || response.text() == null ? "" : response.text().strip();
		if (text.isEmpty()) {
			throw new ExternalServiceException(Kind.INVALID_RESPONSE, SERVICE);
		}
		return text;
	}

	private Response post(byte[] audio) {
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentType(MediaType.parseMediaType("audio/ogg"));
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		form.add("file", new HttpEntity<>(new ByteArrayResource(audio) {
			@Override
			public String getFilename() {
				return "voice.ogg";
			}
		}, fileHeaders));
		form.add("model", settings.model());
		form.add("language", settings.language());
		form.add("response_format", "json");
		form.add("temperature", "0");
		return client.post().uri("/audio/transcriptions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(form)
				.retrieve()
				.body(Response.class);
	}
}
