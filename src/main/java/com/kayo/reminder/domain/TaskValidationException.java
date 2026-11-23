package com.kayo.reminder.domain;

/** A task broke a validity rule. Names the rule only; the message never embeds what the user wrote. */
public class TaskValidationException extends RuntimeException {

	public enum Rule {
		TITLE_REQUIRED, TITLE_TOO_LONG, URGENCY_REQUIRED, DEADLINE_REQUIRED, DEADLINE_NOT_IN_FUTURE,
		START_AFTER_DEADLINE
	}

	private final Rule rule;

	public TaskValidationException(Rule rule) {
		super("Invalid task: " + rule);
		this.rule = rule;
	}

	public Rule rule() {
		return rule;
	}
}
