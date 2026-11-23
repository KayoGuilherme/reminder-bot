package com.kayo.reminder.testsupport;

import lombok.Value;

@Value
public class FakeReply {

	int status;
	String body;
	long delayMillis;
}
