package com.kayo.reminder.config;

import com.kayo.reminder.transport.http.RestClients;
import com.kayo.reminder.transport.http.SingleRetry;
import com.kayo.reminder.transport.stt.GroqTranscriptionClient;
import com.kayo.reminder.transport.stt.Transcriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SttConfiguration {

	@Bean
	Transcriber transcriptionPort(AppProperties properties) {
		SttProperties stt = properties.stt();
		return new GroqTranscriptionClient(
				RestClients.create(stt.baseUrl(), stt.connectTimeout(), stt.readTimeout()),
				properties.groq().apiKey(), stt, new SingleRetry("Transcription", properties.resilience().retryBackoff()));
	}
}
