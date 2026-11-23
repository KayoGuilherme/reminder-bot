package com.kayo.reminder.application.dto.response;

import com.kayo.reminder.domain.models.enums.ReminderKind;

public record ReminderDecisionResponse(int stage, ReminderKind kind, int repeat) {

	public static ReminderDecisionResponse scheduled(int stage) {
		return new ReminderDecisionResponse(stage, ReminderKind.SCHEDULED, 0);
	}

	public static ReminderDecisionResponse overdue(int stage, int repeat) {
		return new ReminderDecisionResponse(stage, ReminderKind.OVERDUE, repeat);
	}

	public static ReminderDecisionResponse finalStop(int stage, int repeats) {
		return new ReminderDecisionResponse(stage, ReminderKind.FINAL, repeats);
	}
}
