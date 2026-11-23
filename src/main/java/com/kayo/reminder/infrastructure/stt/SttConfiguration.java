package com.kayo.reminder.infrastructure.stt;

import com.kayo.reminder.application.port.out.TranscriptionPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.RestClients;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SttConfiguration {

	@Bean
	TranscriptionPort transcriptionPort(AppProperties properties) {
		AppProperties.Stt stt = properties.stt();
		return new GroqTranscriptionClient(
				RestClients.create(stt.baseUrl(), stt.connectTimeout(), stt.readTimeout()),
				properties.groq().apiKey(), stt, new SingleRetry("Transcription", properties.resilience().retryBackoff()));
	}
}
