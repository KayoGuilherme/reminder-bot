package com.kayo.reminder.application.port.out;

public interface AudioDownloadPort {

	/**
	 * Fetches the bytes of a voice message (OGG/Opus) into memory.
	 *
	 * @throws ExternalServiceException if the file cannot be fetched
	 */
	byte[] download(String fileId);
}
