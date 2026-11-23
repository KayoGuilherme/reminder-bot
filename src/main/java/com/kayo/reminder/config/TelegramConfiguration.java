package com.kayo.reminder.config;

import com.kayo.reminder.application.service.IncomingCommandService;
import com.kayo.reminder.transport.http.RestClients;
import com.kayo.reminder.transport.http.SingleRetry;
import com.kayo.reminder.transport.telegram.AudioDownloader;
import com.kayo.reminder.transport.telegram.MessageSender;
import com.kayo.reminder.transport.telegram.TelegramAudioDownloader;
import com.kayo.reminder.transport.telegram.TelegramBot;
import com.kayo.reminder.transport.telegram.TelegramMessageSender;
import com.kayo.reminder.transport.telegram.TelegramStartupCheck;
import com.kayo.reminder.transport.telegram.TelegramUpdateConsumer;
import com.kayo.reminder.transport.telegram.TelegramUpdateMapper;
import com.kayo.reminder.transport.telegram.UpdateAllowlist;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory.DefaultOkHttpClientCreator;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramConfiguration {

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

	@Bean
	AudioDownloader audioDownloadPort(AppProperties properties) {
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
	MessageSender messageSenderPort(TelegramClient client, AppProperties properties) {
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
			IncomingCommandService commands) {
		return new TelegramUpdateConsumer(allowlist, mapper, commands);
	}

	@Bean
	TelegramBot telegramBot(AppProperties properties, TelegramUpdateConsumer consumer) {
		return new TelegramBot(properties.telegram().botToken(), consumer);
	}
}
