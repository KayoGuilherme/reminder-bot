package com.kayo.reminder.testsupport;

import lombok.Value;

@Value
public class SentEdit {

	long chatId;
	int messageId;
	String text;
}
