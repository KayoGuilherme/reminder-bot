package com.kayo.reminder.application.port.out;

/**
 * An outbound call to an external service failed. The message names the service and the kind only, never a payload,
 * header or credential.
 */
public class ExternalServiceException extends RuntimeException {

	public enum Kind {
		/** Timeout, network error, rate limit or server error, still failing after the allowed retry. */
		UNAVAILABLE,
		/** The service refused the request and repeating it cannot help (bad key, bad request, unknown file). */
		REJECTED,
		/** The service answered, but not with anything usable (malformed or empty). */
		INVALID_RESPONSE
	}

	private final Kind kind;

	public ExternalServiceException(Kind kind, String service) {
		super(service + " failed: " + kind);
		this.kind = kind;
	}

	public ExternalServiceException(Kind kind, String service, Throwable cause) {
		super(service + " failed: " + kind, cause);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}
}
