package com.kayo.reminder.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures what a class logs so tests can assert on it. Call {@link #close()} when done. */
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

	/** Captures everything logged anywhere, at DEBUG. */
	public static LogCapture ofRoot() {
		return new LogCapture((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME));
	}

	/**
	 * The worst case the configuration allows: the root logger (and so everything of ours) at DEBUG, with the
	 * library levels pinned in {@code application.yaml} applied, so that those pins are tested too.
	 */
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

	/** A snapshot; other threads (a fake server's) may still be logging, so a clash with a writer is retried. */
	public List<ILoggingEvent> events() {
		while (true) {
			try {
				return List.copyOf(appender.list);
			}
			catch (java.util.ConcurrentModificationException retry) {
				Thread.onSpinWait();
			}
		}
	}

	public List<ILoggingEvent> events(Level level) {
		return events().stream().filter(e -> e.getLevel() == level).toList();
	}

	/** Every formatted message plus any exception text, for "must not contain" assertions. */
	public String allText() {
		return allText(e -> true);
	}

	/** Like {@link #allText()} but only for events {@code accept} lets through. */
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
