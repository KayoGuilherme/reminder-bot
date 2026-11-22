package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.service.IncomingCommandService;
import com.kayo.reminder.application.service.Messages;
import com.kayo.reminder.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Checks that app.telegram.enabled really switches the whole Telegram side on and off. */
class TelegramWiringTest {

	private ApplicationContextRunner runner(String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration.class))
				.withUserConfiguration(Config.class)
				.withPropertyValues("TELEGRAM_BOT_TOKEN=wiring-token", "TELEGRAM_ALLOWED_USER_ID=42",
						"GROQ_API_KEY=g", "GEMINI_API_KEY=m")
				.withPropertyValues(overrides);
	}

	@Test
	void enabledByDefaultCreatesBotConsumerSenderAndClient() {
		runner().run(context -> {
			assertThat(context).hasNotFailed();
			SpringLongPollingBot bot = context.getBean(SpringLongPollingBot.class);
			assertThat(bot.getBotToken()).isEqualTo("wiring-token");
			assertThat(bot.getUpdatesConsumer()).isInstanceOf(TelegramUpdateConsumer.class);
			assertThat(context).hasSingleBean(MessageSenderPort.class);
			assertThat(context).hasSingleBean(TelegramClient.class);
			assertThat(context).hasSingleBean(HandleIncomingCommandUseCase.class);
		});
	}

	@Test
	void disabledCreatesNoTelegramBeansAndNothingIsRegisteredWithTelegram() {
		runner("APP_TELEGRAM_ENABLED=false")
				.withConfiguration(AutoConfigurations.of(TelegramBotStarterConfiguration.class))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(SpringLongPollingBot.class);
					assertThat(context).doesNotHaveBean(MessageSenderPort.class);
					assertThat(context).doesNotHaveBean(TelegramClient.class);
					assertThat(context).doesNotHaveBean(HandleIncomingCommandUseCase.class);
					assertThat(context).doesNotHaveBean(TelegramUpdateConsumer.class);
				});
	}

	@EnableConfigurationProperties(AppProperties.class)
	@Import({ TelegramConfiguration.class, IncomingCommandService.class })
	static class Config {

		@Bean
		Messages messages(MessageSource source, AppProperties properties) {
			return new Messages(source, properties);
		}
	}
}
