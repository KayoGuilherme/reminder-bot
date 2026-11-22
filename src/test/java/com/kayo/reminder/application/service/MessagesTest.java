package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;

class MessagesTest {

	private static final String[] VALID = {
			"TELEGRAM_BOT_TOKEN=bot-token-secret",
			"TELEGRAM_ALLOWED_USER_ID=12345",
			"GROQ_API_KEY=groq-secret",
			"GEMINI_API_KEY=gemini-secret"
	};

	private ApplicationContextRunner runner(String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration.class))
				.withUserConfiguration(Config.class)
				.withPropertyValues(VALID)
				.withPropertyValues(overrides);
	}

	@Test
	void englishReturnsEnglishText() {
		runner("APP_LANGUAGE=en").run(context -> {
			Messages messages = context.getBean(Messages.class);
			assertThat(messages.get("usage")).contains("task reminder bot");
			assertThat(messages.get("not-supported-yet")).isEqualTo("Not supported yet.");
		});
	}

	@Test
	void portugueseReturnsPortugueseText() {
		runner("APP_LANGUAGE=pt-BR").run(context -> {
			Messages messages = context.getBean(Messages.class);
			assertThat(messages.get("usage")).contains("bot de lembretes");
			assertThat(messages.get("not-supported-yet")).isEqualTo("Ainda não é suportado.");
		});
	}

	@Test
	void missingPortugueseKeyFallsBackToEnglish() {
		runner("APP_LANGUAGE=pt-BR", "spring.messages.basename=fallback-test").run(context -> {
			Messages messages = context.getBean(Messages.class);
			assertThat(messages.get("shared")).isEqualTo("compartilhado");
			assertThat(messages.get("english-only")).isEqualTo("english only");
		});
	}

	@Test
	void hostDefaultLocaleDoesNotAffectEnglish() {
		Locale original = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("pt-BR"));
		try {
			runner("APP_LANGUAGE=en").run(context ->
					assertThat(context.getBean(Messages.class).get("not-supported-yet")).isEqualTo("Not supported yet."));
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void bundlesDefineTheSameKeys() throws IOException {
		Properties english = load("/messages.properties");
		Properties portuguese = load("/messages_pt_BR.properties");
		assertThat(portuguese.stringPropertyNames()).containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
	}

	private Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = getClass().getResourceAsStream(resource)) {
			assertThat(in).as(resource).isNotNull();
			properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return properties;
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class Config {

		@Bean
		Messages messages(MessageSource source, AppProperties properties) {
			return new Messages(source, properties);
		}
	}
}
