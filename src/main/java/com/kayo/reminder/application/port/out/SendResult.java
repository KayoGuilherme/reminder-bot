package com.kayo.reminder.application.port.out;

/**
 * Outcome of an outbound operation. Callers check {@link #success()}; failures are reported here, never thrown.
 *
 * @param messageId id of the sent or edited message when known, otherwise {@code null}
 * @param failure reason when {@code success} is false, otherwise {@code null}
 */
public record SendResult(boolean success, Integer messageId, FailureReason failure) {

	public enum FailureReason {
		/** Telegram rate-limited us and the wait was too long (or retries ran out). */
		RATE_LIMITED,
		/** Network error, timeout or server error that persisted after all retries. */
		TRANSIENT,
		/** Telegram refused the request and repeating it cannot help (bad chat, blocked bot, ...). */
		REJECTED,
		/** Anything else, including interruption. */
		UNEXPECTED
	}

	public SendResult {
		if (success == (failure != null)) {
			throw new IllegalArgumentException("A result has a failure reason if and only if it failed");
		}
	}

	public static SendResult ok(Integer messageId) {
		return new SendResult(true, messageId, null);
	}

	public static SendResult failed(FailureReason reason) {
		return new SendResult(false, null, reason);
	}
}
