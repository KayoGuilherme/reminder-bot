package com.kayo.reminder.application.port.in;

/**
 * A user action, independent of Telegram. Exactly one of {@code text}, {@code voiceFileId} and {@code callbackId}
 * is set; {@link #kind()} says which.
 *
 * @param messageId the message itself, or for a callback the message holding the pressed button
 */
public record IncomingCommand(
		long chatId,
		long userId,
		Integer messageId,
		String text,
		String voiceFileId,
		Integer voiceDurationSeconds,
		String callbackId,
		String callbackData) {

	public enum Kind { TEXT, VOICE, CALLBACK }

	public IncomingCommand {
		int populated = (text != null ? 1 : 0) + (voiceFileId != null ? 1 : 0) + (callbackId != null ? 1 : 0);
		if (populated != 1) {
			throw new IllegalArgumentException("Exactly one of text, voiceFileId and callbackId must be set");
		}
	}

	public static IncomingCommand text(long chatId, long userId, Integer messageId, String text) {
		return new IncomingCommand(chatId, userId, messageId, text, null, null, null, null);
	}

	public static IncomingCommand voice(long chatId, long userId, Integer messageId, String fileId,
			Integer durationSeconds) {
		return new IncomingCommand(chatId, userId, messageId, null, fileId, durationSeconds, null, null);
	}

	public static IncomingCommand callback(long chatId, long userId, Integer messageId, String callbackId,
			String callbackData) {
		return new IncomingCommand(chatId, userId, messageId, null, null, null, callbackId, callbackData);
	}

	public Kind kind() {
		if (callbackId != null) {
			return Kind.CALLBACK;
		}
		return voiceFileId != null ? Kind.VOICE : Kind.TEXT;
	}
}
