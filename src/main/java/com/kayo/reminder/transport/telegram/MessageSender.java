package com.kayo.reminder.transport.telegram;

import com.kayo.reminder.application.dto.response.ButtonResponse;
import com.kayo.reminder.application.dto.response.SendResultResponse;
import java.util.List;

public interface MessageSender {

	SendResultResponse sendText(long chatId, String text);

	SendResultResponse sendWithButtons(long chatId, String text, List<List<ButtonResponse>> buttons);

	SendResultResponse editText(long chatId, int messageId, String text);

	SendResultResponse answerCallback(String callbackId);
}
