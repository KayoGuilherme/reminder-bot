package com.kayo.reminder.infrastructure.http;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Runs an outbound call and retries it exactly once when the failure looks transient (I/O error, timeout, HTTP 429
 * or 5xx). Other failures are not retried. Failures are reported as {@link ExternalServiceException} and logged with
 * the service name, kind and status only: an HTTP exception's own message can contain response bodies.
 */
public final class SingleRetry {

	/** Pause between the two attempts; replaced in tests so they never really wait. */
	@FunctionalInterface
	public interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

	/** Lets adapters that are not on {@code RestClient} flag a failure as worth one more try. */
	public static class TransientFailure extends RuntimeException {

		public TransientFailure(String description, Throwable cause) {
			super(description, cause);
		}
	}

	private static final Logger log = LoggerFactory.getLogger(SingleRetry.class);
	private static final Duration DEFAULT_DELAY = Duration.ofMillis(500);

	private final String service;
	private final Duration delay;
	private final Sleeper sleeper;

	public SingleRetry(String service) {
		this(service, DEFAULT_DELAY, duration -> Thread.sleep(duration));
	}

	/** Pauses for {@code delay} between the two attempts (taken from {@code app.resilience.retry-backoff}). */
	public SingleRetry(String service, Duration delay) {
		this(service, delay, duration -> Thread.sleep(duration));
	}

	public SingleRetry(String service, Duration delay, Sleeper sleeper) {
		this.service = service;
		this.delay = delay;
		this.sleeper = sleeper;
	}

	public <T> T call(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (ExternalServiceException ex) {
			throw ex;
		}
		catch (RuntimeException first) {
			if (!isTransient(first)) {
				throw failure(first);
			}
			log.debug("{} failed transiently ({}), retrying once", service, describe(first));
			try {
				sleeper.sleep(delay);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new ExternalServiceException(Kind.UNAVAILABLE, service);
			}
			try {
				return action.get();
			}
			catch (ExternalServiceException ex) {
				throw ex;
			}
			catch (RuntimeException second) {
				throw failure(second);
			}
		}
	}

	/** Shared by adapters that are not on {@code RestClient} (Telegram's own client reports an IOException). */
	public static boolean isTransient(RuntimeException error) {
		if (error instanceof ResourceAccessException || error instanceof TransientFailure) {
			return true;
		}
		if (error instanceof RestClientResponseException response) {
			int status = response.getStatusCode().value();
			return status == 429 || status >= 500;
		}
		return error.getCause() instanceof IOException;
	}

	private ExternalServiceException failure(RuntimeException error) {
		Kind kind;
		if (isTransient(error)) {
			kind = Kind.UNAVAILABLE;
		}
		else if (error instanceof RestClientException && !(error instanceof RestClientResponseException)) {
			// The server answered fine but the body could not be read as expected.
			kind = Kind.INVALID_RESPONSE;
		}
		else {
			kind = Kind.REJECTED;
		}
		log.warn("{} failed: {} ({})", service, kind, describe(error));
		return new ExternalServiceException(kind, service);
	}

	private static String describe(RuntimeException error) {
		if (error instanceof RestClientResponseException response) {
			return "HTTP " + response.getStatusCode().value();
		}
		return error.getClass().getSimpleName();
	}
}
