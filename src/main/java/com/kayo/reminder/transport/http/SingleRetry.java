package com.kayo.reminder.transport.http;

import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class SingleRetry {

	@FunctionalInterface
	public interface Sleeper {
		void sleep(Duration duration) throws InterruptedException;
	}

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
		catch (ExternalServiceException known) {
			throw known;
		}
		catch (RuntimeException first) {
			return retryOrFail(action, first);
		}
	}

	public static boolean isTransient(RuntimeException error) {
		return error instanceof ResourceAccessException || error instanceof TransientFailure
				|| (error instanceof RestClientResponseException response ? isRetryableStatus(response)
						: error.getCause() instanceof IOException);
	}

	private static boolean isRetryableStatus(RestClientResponseException response) {
		int status = response.getStatusCode().value();
		return status == 429 || status >= 500;
	}

	private <T> T retryOrFail(Supplier<T> action, RuntimeException first) {
		Optional.of(first).filter(SingleRetry::isTransient).orElseThrow(() -> failure(first));
		log.debug("{} failed transiently ({}), retrying once", service, describe(first));
		pause();
		return secondAttempt(action);
	}

	private void pause() {
		try {
			sleeper.sleep(delay);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new ExternalServiceException(ExternalFailureKind.UNAVAILABLE, service);
		}
	}

	private <T> T secondAttempt(Supplier<T> action) {
		try {
			return action.get();
		}
		catch (ExternalServiceException known) {
			throw known;
		}
		catch (RuntimeException second) {
			throw failure(second);
		}
	}

	private ExternalServiceException failure(RuntimeException error) {
		ExternalFailureKind kind = kindOf(error);
		log.warn("{} failed: {} ({})", service, kind, describe(error));
		return new ExternalServiceException(kind, service);
	}

	private static ExternalFailureKind kindOf(RuntimeException error) {
		return Optional.of(error).filter(SingleRetry::isTransient).map(transientError -> ExternalFailureKind.UNAVAILABLE)
				.or(() -> Optional.of(error).filter(SingleRetry::isUnreadable).map(unreadable -> ExternalFailureKind.INVALID_RESPONSE))
				.orElse(ExternalFailureKind.REJECTED);
	}

	private static boolean isUnreadable(RuntimeException error) {
		return error instanceof RestClientException && !(error instanceof RestClientResponseException);
	}

	private static String describe(RuntimeException error) {
		return error instanceof RestClientResponseException response ? "HTTP " + response.getStatusCode().value()
				: error.getClass().getSimpleName();
	}
}
