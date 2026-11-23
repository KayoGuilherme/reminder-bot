package com.kayo.reminder.infrastructure.logging;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Puts a short random id in the logging context (key {@value #KEY}) for as long as the scope is open, so every log
 * line of one update or one scheduled run carries it. Closing restores whatever was there before (normally nothing),
 * so the id never leaks into the next piece of work on the same thread.
 */
public final class CorrelationId implements AutoCloseable {

	public static final String KEY = "cid";

	private final String previous;
	private final String id;

	private CorrelationId() {
		this.previous = MDC.get(KEY);
		this.id = UUID.randomUUID().toString().substring(0, 8);
		MDC.put(KEY, id);
	}

	public static CorrelationId open() {
		return new CorrelationId();
	}

	public String id() {
		return id;
	}

	@Override
	public void close() {
		if (previous == null) {
			MDC.remove(KEY);
		}
		else {
			MDC.put(KEY, previous);
		}
	}
}
