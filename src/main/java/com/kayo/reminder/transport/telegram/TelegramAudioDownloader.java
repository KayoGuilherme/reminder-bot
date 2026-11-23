package com.kayo.reminder.transport.telegram;

import com.kayo.reminder.domain.exceptions.ExternalServiceException;
import com.kayo.reminder.domain.models.enums.ExternalFailureKind;
import com.kayo.reminder.transport.http.SingleRetry;
import com.kayo.reminder.transport.http.SingleRetry.TransientFailure;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class TelegramAudioDownloader implements AudioDownloader {

	private final TelegramClient client;
	private final RestClient files;
	private final String botToken;
	private final SingleRetry retry;

	public TelegramAudioDownloader(TelegramClient client, RestClient files, String botToken, SingleRetry retry) {
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
			String path = Optional.ofNullable(file).map(File::getFilePath)
					.orElseThrow(TelegramAudioDownloader::invalidResponse);
			return Optional.ofNullable(files.get().uri("/file/bot{token}/{path}", botToken, path).retrieve()
					.body(byte[].class)).filter(audio -> audio.length > 0)
					.orElseThrow(TelegramAudioDownloader::invalidResponse);
		}
		catch (TelegramApiException failure) {
			throw classify(failure);
		}
	}

	private static RuntimeException classify(TelegramApiException error) {
		Optional<Integer> code = Optional.of(error).filter(TelegramApiRequestException.class::isInstance)
				.map(TelegramApiRequestException.class::cast)
				.map(request -> Optional.ofNullable(request.getErrorCode()).orElse(0));
		return code.filter(value -> value == 429 || value >= 500)
				.<RuntimeException>map(value -> new TransientFailure("http " + value, error))
				.or(() -> code.filter(value -> value >= 400).map(value -> rejected()))
				.or(() -> ioFailure(error))
				.orElseGet(TelegramAudioDownloader::rejected);
	}

	private static Optional<RuntimeException> ioFailure(TelegramApiException error) {
		return Stream.iterate(error.getCause(), Objects::nonNull, Throwable::getCause)
				.filter(IOException.class::isInstance).findFirst()
				.map(cause -> new TransientFailure("io", error));
	}

	private static ExternalServiceException invalidResponse() {
		return new ExternalServiceException(ExternalFailureKind.INVALID_RESPONSE, "Telegram download");
	}

	private static ExternalServiceException rejected() {
		return new ExternalServiceException(ExternalFailureKind.REJECTED, "Telegram download");
	}
}
