package com.kayo.reminder.domain.exceptions;

import com.kayo.reminder.domain.models.enums.ExternalFailureKind;

public class ExternalServiceException extends RuntimeException {

	private final ExternalFailureKind kind;

	public ExternalServiceException(ExternalFailureKind kind, String service) {
		super(service + " failed: " + kind);
		this.kind = kind;
	}

	public ExternalServiceException(ExternalFailureKind kind, String service, Throwable cause) {
		super(service + " failed: " + kind, cause);
		this.kind = kind;
	}

	public ExternalFailureKind kind() {
		return kind;
	}
}
