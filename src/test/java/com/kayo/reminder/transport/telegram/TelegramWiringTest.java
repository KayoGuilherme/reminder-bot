package com.kayo.reminder.transport.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kayo.reminder.application.service.CreateTaskFromVoiceService;
import com.kayo.reminder.application.service.IncomingCommandService;
import com.kayo.reminder.application.service.ListTasksService;
import com.kayo.reminder.application.service.ReminderActionService;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.config.ReminderConfiguration;
import com.kayo.reminder.config.TelegramConfiguration;
import com.kayo.reminder.domain.repositories.TaskRepository;
import com.kayo.reminder.transport.llm.TaskParser;
import com.kayo.reminder.transport.stt.Transcriber;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
			assertThat(context).hasSingleBean(MessageSender.class);
			assertThat(context).hasSingleBean(TelegramClient.class);
			assertThat(context).hasSingleBean(IncomingCommandService.class);
			assertThat(context).hasSingleBean(AudioDownloader.class);
			assertThat(context).hasSingleBean(CreateTaskFromVoiceService.class);
			assertThat(context).hasSingleBean(ReminderActionService.class);
			assertThat(context).hasSingleBean(ListTasksService.class);
			assertThat(context).hasSingleBean(TelegramBotsLongPollingApplication.class);
			assertThat(context).hasBean("telegramStartupCheck");
		});
	}

	@Test
	void disabledCreatesNoTelegramBeansAndNothingIsRegisteredWithTelegram() {
		runner("APP_TELEGRAM_ENABLED=false")
				.withConfiguration(AutoConfigurations.of(TelegramBotStarterConfiguration.class))
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(SpringLongPollingBot.class);
					assertThat(context).doesNotHaveBean(MessageSender.class);
					assertThat(context).doesNotHaveBean(TelegramClient.class);
					assertThat(context).doesNotHaveBean(IncomingCommandService.class);
					assertThat(context).doesNotHaveBean(AudioDownloader.class);
					assertThat(context).doesNotHaveBean(CreateTaskFromVoiceService.class);
					assertThat(context).doesNotHaveBean(ReminderActionService.class);
					assertThat(context).doesNotHaveBean(TelegramUpdateConsumer.class);
					assertThat(context).doesNotHaveBean(ListTasksService.class);
					assertThat(context).doesNotHaveBean("telegramStartupCheck");
				});
	}

	@EnableConfigurationProperties(AppProperties.class)
	@Import({ TelegramConfiguration.class, ReminderConfiguration.class })
	@ComponentScan("com.kayo.reminder.application")
	static class Config {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

		@Bean
		Transcriber transcriptionPort() {
			return mock(Transcriber.class);
		}

		@Bean
		TaskParser taskParserPort() {
			return mock(TaskParser.class);
		}

		@Bean
		TaskRepository taskRepository() {
			return mock(TaskRepository.class);
		}
	}
}
