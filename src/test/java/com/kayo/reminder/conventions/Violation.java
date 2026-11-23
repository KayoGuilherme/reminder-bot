package com.kayo.reminder.conventions;

import lombok.Value;

@Value
public class Violation {

	String rule;
	String file;
	int line;
	String message;

	@Override
	public String toString() {
		return rule + " " + file + ":" + line + " " + message;
	}
}
