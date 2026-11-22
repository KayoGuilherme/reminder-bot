package com.kayo.reminder.application.port.out;

import java.nio.charset.StandardCharsets;

/** An inline button; the callback data comes back when the user presses it. */
public record Button(String text, String callbackData) {

	/** Telegram rejects callback data longer than 64 bytes. */
	public static final int MAX_CALLBACK_DATA_BYTES = 64;

	public Button {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Button text must not be blank");
		}
		if (callbackData == null || callbackData.isEmpty()) {
			throw new IllegalArgumentException("Button callback data must not be empty");
		}
		if (callbackData.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_DATA_BYTES) {
			throw new IllegalArgumentException(
					"Button callback data must be at most " + MAX_CALLBACK_DATA_BYTES + " bytes in UTF-8");
		}
	}
}
