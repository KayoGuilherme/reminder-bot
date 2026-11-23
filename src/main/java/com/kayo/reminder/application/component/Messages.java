package com.kayo.reminder.application.component;

import com.kayo.reminder.config.AppProperties;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class Messages {

	private static final Set<String> SUPPORTED_LANGUAGES = Set.of("pt-BR", "en");

	private final MessageSource messageSource;
	private final Locale locale;

	@Autowired
	public Messages(MessageSource messageSource, AppProperties properties) {
		this(messageSource, Locale.forLanguageTag(properties.language()));
	}

	public Messages(MessageSource messageSource, Locale locale) {
		this.messageSource = messageSource;
		this.locale = locale;
	}

	public Messages forLanguage(String languageTag) {
		return Optional.ofNullable(languageTag).filter(SUPPORTED_LANGUAGES::contains).map(Locale::forLanguageTag)
				.filter(requested -> !requested.equals(locale)).map(requested -> new Messages(messageSource, requested))
				.orElse(this);
	}

	public String get(String key, Object... args) {
		return messageSource.getMessage(key, args, locale);
	}

	public String languageTag() {
		return locale.toLanguageTag();
	}
}
