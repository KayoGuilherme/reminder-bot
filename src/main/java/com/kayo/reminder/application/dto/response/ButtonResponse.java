package com.kayo.reminder.application.dto.response;

public record ButtonResponse(String text, String callbackData) {

	public static final int MAX_CALLBACK_DATA_BYTES = 64;
}
