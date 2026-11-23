package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.service.Messages;
import com.kayo.reminder.config.AppProperties;
import java.time.ZoneId;
import org.springframework.context.support.ResourceBundleMessageSource;

public final class TestMessages {

	private TestMessages() {
	}

	public static ResourceBundleMessageSource source() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		return source;
	}

	public static Messages of(String languageTag) {
		return new Messages(source(), TestProperties.of(ZoneId.of("UTC"), languageTag));
	}

	/** The raw text of a key in a language, for expected values. */
	public static String text(String languageTag, String key, Object... args) {
		return of(languageTag).get(key, args);
	}
}
