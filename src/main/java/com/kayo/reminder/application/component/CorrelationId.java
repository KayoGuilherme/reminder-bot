package com.kayo.reminder.application.component;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

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
		Optional.ofNullable(previous).ifPresentOrElse(value -> MDC.put(KEY, value), () -> MDC.remove(KEY));
	}
}
