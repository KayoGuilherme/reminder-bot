package com.kayo.reminder.transport.telegram;

public interface AudioDownloader {

	byte[] download(String fileId);
}
