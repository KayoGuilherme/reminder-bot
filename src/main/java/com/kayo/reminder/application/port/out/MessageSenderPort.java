package com.kayo.reminder.application.port.out;

import java.util.List;

/** Outbound messaging. Implementations never throw: transport problems come back as a failed {@link SendResult}. */
public interface MessageSenderPort {

	SendResult sendText(long chatId, String text);

	/** Sends {@code text} with inline buttons laid out in {@code buttons} (one inner list per row). */
	SendResult sendWithButtons(long chatId, String text, List<List<Button>> buttons);

	SendResult editText(long chatId, int messageId, String text);

	/** Acknowledges a button press so the client stops showing progress; shows no message. */
	SendResult answerCallback(String callbackId);
}
