package com.kayo.reminder.transport.telegram;

import lombok.Value;

@Value
class TelegramRequest {

	String path;
	String body;
}
