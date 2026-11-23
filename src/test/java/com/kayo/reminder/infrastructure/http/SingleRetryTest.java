package com.kayo.reminder.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.testsupport.LogCapture;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class SingleRetryTest {

	private final List<Duration> sleeps = new ArrayList<>();
	private final SingleRetry retry = new SingleRetry("Fake", Duration.ofMillis(5), sleeps::add);

	private static HttpServerErrorException serverError(HttpStatus status) {
		return HttpServerErrorException.create(status, "x", HttpHeaders.EMPTY, "secret-body".getBytes(), null);
	}

	@Test
	void successOnFirstTryMakesOneCall() {
		AtomicInteger calls = new AtomicInteger();
		assertThat(retry.call(() -> { calls.incrementAndGet(); return "ok"; })).isEqualTo("ok");
		assertThat(calls).hasValue(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void serverErrorThenSuccessMakesTwoCallsWithOnePause() {
		AtomicInteger calls = new AtomicInteger();
		String result = retry.call(() -> {
			if (calls.incrementAndGet() == 1) {
				throw serverError(HttpStatus.SERVICE_UNAVAILABLE);
			}
			return "ok";
		});
		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(2);
		assertThat(sleeps).containsExactly(Duration.ofMillis(5));
	}

	@Test
	void rateLimitIsRetried() {
		AtomicInteger calls = new AtomicInteger();
		retry.call(() -> {
			if (calls.incrementAndGet() == 1) {
				throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", HttpHeaders.EMPTY,
						new byte[0], null);
			}
			return "ok";
		});
		assertThat(calls).hasValue(2);
	}

	@Test
	void twoTimeoutsStopAtTwoCallsAndReportUnavailable() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.call(() -> {
			calls.incrementAndGet();
			throw new ResourceAccessException("timeout", new IOException("secret-url"));
		})).isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.UNAVAILABLE));
		assertThat(calls).hasValue(2);
	}

	@Test
	void unauthorizedIsNotRetriedAndReportsRejected() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.call(() -> {
			calls.incrementAndGet();
			throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "x", HttpHeaders.EMPTY,
					"secret-body".getBytes(), null);
		})).isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.REJECTED));
		assertThat(calls).hasValue(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void externalServiceExceptionsPassThroughUntouched() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.call(() -> {
			calls.incrementAndGet();
			throw new ExternalServiceException(Kind.INVALID_RESPONSE, "Fake");
		})).isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.INVALID_RESPONSE));
		assertThat(calls).hasValue(1);
	}

	@Test
	void transientFailureFlaggedByAdaptersIsRetried() {
		AtomicInteger calls = new AtomicInteger();
		retry.call(() -> {
			if (calls.incrementAndGet() == 1) {
				throw new SingleRetry.TransientFailure("io", new IOException());
			}
			return "ok";
		});
		assertThat(calls).hasValue(2);
	}

	@Test
	void exceptionAndLogsNeverCarryThePayload() {
		try (LogCapture logs = new LogCapture(SingleRetry.class)) {
			assertThatThrownBy(() -> retry.call(() -> { throw serverError(HttpStatus.BAD_GATEWAY); }))
					.isInstanceOf(ExternalServiceException.class)
					.hasMessageNotContaining("secret-body")
					.hasNoCause();
			assertThat(logs.allText()).contains("HTTP 502").doesNotContain("secret-body");
		}
	}

	@Test
	void interruptedPauseReportsUnavailableAndKeepsTheInterruptFlag() {
		SingleRetry interrupted = new SingleRetry("Fake", Duration.ofMillis(5), d -> { throw new InterruptedException(); });
		assertThatThrownBy(() -> interrupted.call(() -> { throw serverError(HttpStatus.BAD_GATEWAY); }))
				.isInstanceOf(ExternalServiceException.class);
		assertThat(Thread.interrupted()).isTrue();
	}
}
