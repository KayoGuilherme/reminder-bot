package com.kayo.reminder.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import org.slf4j.LoggerFactory;

public final class LogCapture implements AutoCloseable {

	private final Logger logger;
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
	private final Level previousLevel;

	public LogCapture(Class<?> source) {
		this((Logger) LoggerFactory.getLogger(source));
	}

	private LogCapture(Logger logger) {
		this.logger = logger;
		previousLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		appender.start();
		logger.addAppender(appender);
	}

	public static LogCapture ofRoot() {
		return new LogCapture((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME));
	}

	public static LogCapture ofRootWithConfiguredLibraryLevels() {
		LogCapture logs = ofRoot();
		org.springframework.beans.factory.config.YamlPropertiesFactoryBean yaml =
				new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
		yaml.setResources(new org.springframework.core.io.ClassPathResource("application.yaml"));
		java.util.Properties properties = yaml.getObject();
		properties.stringPropertyNames().stream().filter(name -> name.startsWith("logging.level."))
				.filter(name -> !name.equals("logging.level.root") && !name.startsWith("logging.level.com.kayo"))
				.forEach(name -> ((Logger) LoggerFactory.getLogger(name.substring("logging.level.".length())))
						.setLevel(Level.toLevel(properties.getProperty(name))));
		return logs;
	}

	public List<ILoggingEvent> events() {
		return snapshot().orElseGet(this::events);
	}

	private Optional<List<ILoggingEvent>> snapshot() {
		try {
			return Optional.of(List.copyOf(appender.list));
		}
		catch (ConcurrentModificationException retry) {
			Thread.onSpinWait();
			return Optional.empty();
		}
	}

	public List<ILoggingEvent> events(Level level) {
		return events().stream().filter(e -> e.getLevel() == level).toList();
	}

	public String allText() {
		return allText(e -> true);
	}

	public String allText(java.util.function.Predicate<ILoggingEvent> accept) {
		StringBuilder sb = new StringBuilder();
		for (ILoggingEvent e : events()) {
			if (!accept.test(e)) {
				continue;
			}
			sb.append(e.getFormattedMessage()).append('\n');
			if (e.getThrowableProxy() != null) {
				sb.append(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(e.getThrowableProxy())).append('\n');
			}
		}
		return sb.toString();
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
		logger.setLevel(previousLevel);
		appender.stop();
	}
}
