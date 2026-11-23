package com.kayo.reminder.application.service;

import com.kayo.reminder.config.AppProperties;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Looks up user-facing texts in the language configured by {@code app.language}. */
@Component
public class Messages {

	private final MessageSource messageSource;
	private final Locale locale;

	@Autowired
	public Messages(MessageSource messageSource, AppProperties properties) {
		this(messageSource, Locale.forLanguageTag(properties.language()));
	}

	Messages(MessageSource messageSource, Locale locale) {
		this.messageSource = messageSource;
		this.locale = locale;
	}

	/**
	 * The same texts in the language of a message: {@code "pt-BR"} or {@code "en"}. Anything else (including null)
	 * keeps the configured language.
	 */
	public Messages forLanguage(String languageTag) {
		if ("pt-BR".equals(languageTag) || "en".equals(languageTag)) {
			Locale requested = Locale.forLanguageTag(languageTag);
			return requested.equals(locale) ? this : new Messages(messageSource, requested);
		}
		return this;
	}

	public String get(String key, Object... args) {
		return messageSource.getMessage(key, args, locale);
	}
}
