package com.kayo.reminder.infrastructure.telegram;

import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.port.out.AudioDownloadPort;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import com.kayo.reminder.infrastructure.http.RestClients;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** The whole Telegram adapter exists only when {@code app.telegram.enabled} is true (the default). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramConfiguration {

	@Bean
	TelegramClient telegramClient(AppProperties properties) {
		return new OkHttpTelegramClient(properties.telegram().botToken());
	}

	/** Downloads use their own client so the configured timeouts do not change how messages are sent. */
	@Bean
	AudioDownloadPort audioDownloadPort(AppProperties properties) {
		OkHttpClient http = new OkHttpClient.Builder()
				.connectTimeout(properties.stt().connectTimeout())
				.readTimeout(properties.stt().readTimeout())
				.build();
		RestClient files = RestClients.create("https://api.telegram.org", properties.stt().connectTimeout(),
				properties.stt().readTimeout());
		return new TelegramAudioDownloader(new OkHttpTelegramClient(http, properties.telegram().botToken()), files,
				properties.telegram().botToken(), new SingleRetry("Telegram download"));
	}

	@Bean
	MessageSenderPort messageSenderPort(TelegramClient client, AppProperties properties) {
		return new TelegramMessageSender(client, properties.telegram().retry(), properties.telegram().botToken());
	}

	@Bean
	UpdateAllowlist updateAllowlist(AppProperties properties) {
		return new UpdateAllowlist(properties.telegram().allowedUserId());
	}

	@Bean
	TelegramUpdateMapper telegramUpdateMapper() {
		return new TelegramUpdateMapper();
	}

	@Bean
	TelegramUpdateConsumer telegramUpdateConsumer(UpdateAllowlist allowlist, TelegramUpdateMapper mapper,
			HandleIncomingCommandUseCase useCase) {
		return new TelegramUpdateConsumer(allowlist, mapper, useCase);
	}

	@Bean
	TelegramBot telegramBot(AppProperties properties, TelegramUpdateConsumer consumer) {
		return new TelegramBot(properties.telegram().botToken(), consumer);
	}
}
