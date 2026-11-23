package com.kayo.reminder.transport.stt;

public interface Transcriber {

	String transcribe(byte[] audio);
}
