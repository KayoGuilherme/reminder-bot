package com.kayo.reminder.application.dto.request;

import com.kayo.reminder.domain.models.enums.CommandKind;

public record IncomingCommandRequest(
		CommandKind kind,
		long chatId,
		long userId,
		Integer messageId,
		String text,
		String voiceFileId,
		Integer voiceDurationSeconds,
		String callbackId,
		String callbackData) {

	public static IncomingCommandRequest text(long chatId, long userId, Integer messageId, String text) {
		return new IncomingCommandRequest(CommandKind.TEXT, chatId, userId, messageId, text, null, null, null, null);
	}

	public static IncomingCommandRequest voice(long chatId, long userId, Integer messageId, String fileId,
			Integer durationSeconds) {
		return new IncomingCommandRequest(CommandKind.VOICE, chatId, userId, messageId, null, fileId,
				durationSeconds, null, null);
	}

	public static IncomingCommandRequest callback(long chatId, long userId, Integer messageId, String callbackId,
			String callbackData) {
		return new IncomingCommandRequest(CommandKind.CALLBACK, chatId, userId, messageId, null, null, null,
				callbackId, callbackData);
	}
}
