package com.kayo.reminder.application.dto.response;

import com.kayo.reminder.domain.models.enums.SendFailureReason;

public record SendResultResponse(boolean success, Integer messageId, SendFailureReason failure) {

	public static SendResultResponse ok(Integer messageId) {
		return new SendResultResponse(true, messageId, null);
	}

	public static SendResultResponse failed(SendFailureReason reason) {
		return new SendResultResponse(false, null, reason);
	}
}
