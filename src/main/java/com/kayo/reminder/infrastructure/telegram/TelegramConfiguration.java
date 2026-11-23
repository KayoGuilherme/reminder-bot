package com.kayo.reminder.infrastructure.telegram;

import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.port.out.AudioDownloadPort;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import com.kayo.reminder.infrastructure.http.RestClients;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory.DefaultOkHttpClientCreator;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** The whole Telegram adapter exists only when {@code app.telegram.enabled} is true (the default). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramConfiguration {

	/**
	 * The long-polling request waits up to 50 seconds for news, so its read timeout must stay above that; it still
	 * ends after this bounded time, and connecting is cut at the configured connect timeout.
	 */
	private static final Duration LONG_POLL_READ_TIMEOUT = Duration.ofSeconds(75);

	@Bean
	TelegramClient telegramClient(AppProperties properties) {
		return new OkHttpTelegramClient(sendClient(properties), properties.telegram().botToken());
	}

	static OkHttpClient sendClient(AppProperties properties) {
		return new OkHttpClient.Builder()
				.connectTimeout(properties.telegram().connectTimeout())
				.readTimeout(properties.telegram().readTimeout())
				.build();
	}

	static OkHttpClient longPollClient(AppProperties properties) {
		return new DefaultOkHttpClientCreator().get().newBuilder()
				.connectTimeout(properties.telegram().connectTimeout())
				.readTimeout(LONG_POLL_READ_TIMEOUT)
				.build();
	}

	/** Replaces the starter's default application so that its polling client has bounded timeouts too. */
	@Bean
	TelegramBotsLongPollingApplication telegramBotsLongPollingApplication(AppProperties properties) {
		return new TelegramBotsLongPollingApplication(com.fasterxml.jackson.databind.ObjectMapper::new,
				() -> longPollClient(properties));
	}

	@Bean
	ApplicationRunner telegramStartupCheck(TelegramClient client) {
		TelegramStartupCheck check = new TelegramStartupCheck(client);
		return args -> check.run();
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
				properties.telegram().botToken(), new SingleRetry("Telegram download", properties.resilience().retryBackoff()));
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
