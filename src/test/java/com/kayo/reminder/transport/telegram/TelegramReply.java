package com.kayo.reminder.transport.telegram;

import lombok.Value;

@Value
class TelegramReply {

	int status;
	String body;
	boolean repeat;
}
