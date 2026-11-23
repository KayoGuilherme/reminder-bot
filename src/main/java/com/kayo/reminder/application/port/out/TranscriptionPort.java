package com.kayo.reminder.application.port.out;

public interface TranscriptionPort {

	/**
	 * Transcribes OGG/Opus audio to text.
	 *
	 * @return the trimmed, non-blank text
	 * @throws ExternalServiceException if the service fails, or answers with nothing usable (including an empty
	 *         transcript, {@code INVALID_RESPONSE})
	 */
	String transcribe(byte[] audio);
}
