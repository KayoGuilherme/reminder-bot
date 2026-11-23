package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.port.out.Button;
import com.kayo.reminder.application.port.out.MessageSenderPort;
import com.kayo.reminder.application.port.out.SendResult;
import java.util.ArrayList;
import java.util.List;

/** Records everything sent so tests can assert on it. */
public class RecordingSender implements MessageSenderPort {

	public record Text(long chatId, String text) {
	}

	public record WithButtons(long chatId, String text, List<List<Button>> buttons) {
	}

	public record Edit(long chatId, int messageId, String text) {
	}

	public final List<Text> texts = new ArrayList<>();
	public final List<WithButtons> withButtons = new ArrayList<>();
	public final List<Edit> edits = new ArrayList<>();
	public final List<String> answeredCallbacks = new ArrayList<>();
	public SendResult result = SendResult.ok(1);

	@Override
	public SendResult sendText(long chatId, String text) {
		texts.add(new Text(chatId, text));
		return result;
	}

	@Override
	public SendResult sendWithButtons(long chatId, String text, List<List<Button>> buttons) {
		withButtons.add(new WithButtons(chatId, text, buttons));
		return result;
	}

	@Override
	public SendResult editText(long chatId, int messageId, String text) {
		edits.add(new Edit(chatId, messageId, text));
		return result;
	}

	@Override
	public SendResult answerCallback(String callbackId) {
		answeredCallbacks.add(callbackId);
		return result;
	}
}
