package com.kayo.reminder.infrastructure.telegram;

import com.kayo.reminder.application.port.out.AudioDownloadPort;
import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import com.kayo.reminder.infrastructure.http.SingleRetry.TransientFailure;
import java.io.IOException;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Downloads a voice message into memory: nothing is written to disk. The client is dedicated to downloads so its
 * timeouts do not affect the message sender; failures carry no file id, path or token.
 */
class TelegramAudioDownloader implements AudioDownloadPort {

	private final TelegramClient client;
	private final RestClient files;
	private final String botToken;
	private final SingleRetry retry;

	/**
	 * The library's own download ignores the HTTP status and cannot be pointed at a test server, so {@code GetFile}
	 * goes through {@code client} and the bytes are fetched with {@code files} (rooted at the Telegram API host).
	 */
	TelegramAudioDownloader(TelegramClient client, RestClient files, String botToken, SingleRetry retry) {
		this.client = client;
		this.files = files;
		this.botToken = botToken;
		this.retry = retry;
	}

	@Override
	public byte[] download(String fileId) {
		return retry.call(() -> fetch(fileId));
	}

	private byte[] fetch(String fileId) {
		try {
			File file = client.execute(new GetFile(fileId));
			if (file == null || file.getFilePath() == null) {
				throw new ExternalServiceException(Kind.INVALID_RESPONSE, "Telegram download");
			}
			byte[] audio = files.get().uri("/file/bot{token}/{path}", botToken, file.getFilePath()).retrieve()
					.body(byte[].class);
			if (audio == null || audio.length == 0) {
				throw new ExternalServiceException(Kind.INVALID_RESPONSE, "Telegram download");
			}
			return audio;
		}
		catch (TelegramApiException ex) {
			throw classify(ex);
		}
	}

	private static RuntimeException classify(TelegramApiException error) {
		if (error instanceof TelegramApiRequestException request) {
			int code = request.getErrorCode() == null ? 0 : request.getErrorCode();
			if (code == 429 || code >= 500) {
				return new TransientFailure("http " + code, error);
			}
			if (code >= 400) {
				return new ExternalServiceException(Kind.REJECTED, "Telegram download");
			}
		}
		for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				return new TransientFailure("io", error);
			}
		}
		return new ExternalServiceException(Kind.REJECTED, "Telegram download");
	}
}
