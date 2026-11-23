package com.kayo.reminder.testsupport;

import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import com.kayo.reminder.transport.telegram.MessageSender;
import java.util.ArrayList;
import java.util.List;

public class RecordingSender implements MessageSender {

	public final List<SentText> texts = new ArrayList<>();
	public final List<SentButtons> withButtons = new ArrayList<>();
	public final List<SentEdit> edits = new ArrayList<>();
	public final List<String> answeredCallbacks = new ArrayList<>();
	public SendResultResponse result = SendResultResponse.ok(1);

	@Override
	public SendResultResponse sendText(long chatId, String text) {
		texts.add(new SentText(chatId, text));
		return result;
	}

	@Override
	public SendResultResponse sendWithButtons(long chatId, String text, List<List<ButtonResponse>> buttons) {
		withButtons.add(new SentButtons(chatId, text, buttons));
		return result;
	}

	@Override
	public SendResultResponse editText(long chatId, int messageId, String text) {
		edits.add(new SentEdit(chatId, messageId, text));
		return result;
	}

	@Override
	public SendResultResponse answerCallback(String callbackId) {
		answeredCallbacks.add(callbackId);
		return result;
	}
}
