package com.kayo.reminder.domain.models.enums;

public enum TaskRule {
	TITLE_REQUIRED("The title is required."),
	TITLE_TOO_LONG("The title is longer than 500 characters."),
	URGENCY_REQUIRED("The urgency is required."),
	DEADLINE_REQUIRED("The deadline is required."),
	DEADLINE_NOT_IN_FUTURE("The deadline must be in the future."),
	START_AFTER_DEADLINE("The start must not be after the deadline."),
	LANGUAGE_UNSUPPORTED("The language must be en or pt-BR.");

	private final String sentence;

	TaskRule(String sentence) {
		this.sentence = sentence;
	}

	public String sentence() {
		return sentence;
	}
}
