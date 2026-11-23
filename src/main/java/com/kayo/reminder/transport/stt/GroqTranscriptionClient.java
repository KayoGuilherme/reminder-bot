package com.kayo.reminder.transport.stt;

import com.kayo.reminder.config.SttProperties;
import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.transport.http.SingleRetry;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class GroqTranscriptionClient implements Transcriber {

	private static final String SERVICE = "Transcription";

	private final RestClient client;
	private final String apiKey;
	private final SttProperties settings;
	private final SingleRetry retry;

	public GroqTranscriptionClient(RestClient client, String apiKey, SttProperties settings, SingleRetry retry) {
		this.client = client;
		this.apiKey = apiKey;
		this.settings = settings;
		this.retry = retry;
	}

	@Override
	public String transcribe(byte[] audio) {
		TranscriptionResponse response = retry.call(() -> post(audio));
		String text = response == null || response.text() == null ? "" : response.text().strip();
		if (text.isEmpty()) {
			throw new ExternalServiceException(ExternalFailureKind.INVALID_RESPONSE, SERVICE);
		}
		return text;
	}

	private TranscriptionResponse post(byte[] audio) {
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
		if (settings.language() != null && !settings.language().isBlank()) {
			form.add("language", settings.language());
		}
		form.add("response_format", "json");
		form.add("temperature", "0");
		return client.post().uri("/audio/transcriptions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(form)
				.retrieve()
				.body(TranscriptionResponse.class);
	}
}
