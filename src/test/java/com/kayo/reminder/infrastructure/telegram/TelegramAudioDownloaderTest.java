package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kayo.reminder.application.port.out.ExternalServiceException;
import com.kayo.reminder.application.port.out.ExternalServiceException.Kind;
import com.kayo.reminder.infrastructure.http.RestClients;
import com.kayo.reminder.infrastructure.http.SingleRetry;
import com.kayo.reminder.testsupport.LogCapture;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TelegramAudioDownloaderTest {

	private static final String FILE_OK = "{\"ok\":true,\"result\":{\"file_id\":\"f1\",\"file_unique_id\":\"u\","
			+ "\"file_size\":7,\"file_path\":\"voice/file_1.oga\"}}";

	private final FakeTelegramApi api = newApi();

	private static FakeTelegramApi newApi() {
		try {
			return new FakeTelegramApi();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@AfterEach
	void stop() {
		api.close();
	}

	private TelegramAudioDownloader downloader() {
		return new TelegramAudioDownloader(api.client(),
				RestClients.create(api.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2)),
				FakeTelegramApi.TOKEN, new SingleRetry("Telegram download", Duration.ZERO, d -> { }));
	}

	@Test
	void returnsTheVoiceBytes() {
		api.reply(200, FILE_OK).reply(200, "OGGDATA");
		assertThat(downloader().download("f1")).isEqualTo("OGGDATA".getBytes());
		assertThat(api.requests()).hasSize(2);
		assertThat(api.requests().get(0).path()).endsWith("/getFile");
		assertThat(api.requests().get(1).path()).isEqualTo("/file/bot" + FakeTelegramApi.TOKEN + "/voice/file_1.oga");
	}

	@Test
	void downloadHttpErrorIsNotMistakenForAudio() {
		api.reply(200, FILE_OK).reply(404, "{\"ok\":false}");
		assertThatThrownBy(() -> downloader().download("f1"))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.REJECTED));
	}

	@Test
	void unknownFileIsRejectedWithoutRetry() {
		api.error(400, "Bad Request: invalid file_id");
		assertThatThrownBy(() -> downloader().download("nope"))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.REJECTED));
		assertThat(api.requests()).hasSize(1);
	}

	@Test
	void badGatewayThenSuccessMakesTwoGetFileRequests() {
		api.error(502, "Bad Gateway").reply(200, FILE_OK).reply(200, "OGGDATA");
		assertThat(downloader().download("f1")).isEqualTo("OGGDATA".getBytes());
		assertThat(api.requests()).hasSize(3);
	}

	@Test
	void persistentServerErrorReportsUnavailableAfterTwoAttempts() {
		api.errorAlways(502, "Bad Gateway");
		assertThatThrownBy(() -> downloader().download("f1"))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.UNAVAILABLE));
		assertThat(api.requests()).hasSize(2);
	}

	@Test
	void tokenNeverAppearsInLogsOrErrors() {
		api.errorAlways(502, "Bad Gateway");
		try (LogCapture logs = new LogCapture(SingleRetry.class)) {
			assertThatThrownBy(() -> downloader().download("f1"))
					.hasMessageNotContaining(FakeTelegramApi.TOKEN);
			assertThat(logs.allText()).doesNotContain(FakeTelegramApi.TOKEN);
		}
	}

	@Test
	void connectionFailureIsReportedUnavailable() {
		api.stop();
		assertThatThrownBy(() -> downloader().download("f1"))
				.isInstanceOfSatisfying(ExternalServiceException.class, ex -> assertThat(ex.kind()).isEqualTo(Kind.UNAVAILABLE));
	}
}
