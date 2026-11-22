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
		logger = (Logger) LoggerFactory.getLogger(source);
		previousLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		appender.start();
		logger.addAppender(appender);
	}

	public List<ILoggingEvent> events() {
		return List.copyOf(appender.list);
	}

	public List<ILoggingEvent> events(Level level) {
		return events().stream().filter(e -> e.getLevel() == level).toList();
	}

	/** Every formatted message plus any exception text, for "must not contain" assertions. */
	public String allText() {
		StringBuilder sb = new StringBuilder();
		for (ILoggingEvent e : appender.list) {
			sb.append(e.getFormattedMessage()).append('\n');
			if (e.getThrowableProxy() != null) {
				sb.append(e.getThrowableProxy().getMessage()).append('\n');
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
