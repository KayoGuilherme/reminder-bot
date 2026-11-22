package com.kayo.reminder.infrastructure.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.testsupport.LogCapture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

class TelegramUpdateConsumerTest {

	private final List<IncomingCommand> handled = new ArrayList<>();
	private final List<String> failOnText = new ArrayList<>();

	private final TelegramUpdateConsumer consumer = new TelegramUpdateConsumer(
			new UpdateAllowlist(42), new TelegramUpdateMapper(), command -> {
				if (command.text() != null && failOnText.contains(command.text())) {
					throw new IllegalStateException("boom");
				}
				handled.add(command);
			});

	@Test
	void allowedUpdateIsHandled() {
		consumer.consume(List.of(Updates.text(1, 42, 100, "/start")));
		assertThat(handled).hasSize(1);
		assertThat(handled.get(0).text()).isEqualTo("/start");
	}

	@Test
	void strangersUpdateNeverReachesTheUseCase() {
		try (LogCapture logs = new LogCapture(UpdateAllowlist.class)) {
			consumer.consume(List.of(Updates.text(1, 99, 100, "/start")));
			assertThat(handled).isEmpty();
			assertThat(logs.events(Level.WARN)).hasSize(1);
		}
	}

	@Test
	void failureInOneUpdateDoesNotStopTheNext() {
		failOnText.add("explode");
		try (LogCapture logs = new LogCapture(TelegramUpdateConsumer.class)) {
			consumer.consume(List.of(
					Updates.text(7, 42, 100, "explode"),
					Updates.text(8, 42, 100, "after")));
			assertThat(handled).extracting(IncomingCommand::text).containsExactly("after");
			assertThat(logs.events(Level.ERROR)).hasSize(1);
			assertThat(logs.allText()).contains("7").doesNotContain("explode");
		}
	}

	@Test
	void malformedUpdatesAreSkippedAndPollingContinues() {
		Update empty = new Update();
		empty.setUpdateId(1);
		Update noSender = Updates.withMessage(2, Updates.message(100, 42, 1).from(null).text("x").build());
		consumer.consume(java.util.Arrays.asList(null, empty, noSender, Updates.text(3, 42, 100, "ok")));
		assertThat(handled).extracting(IncomingCommand::text).containsExactly("ok");
	}

	@Test
	void unsupportedKindFromAllowedUserIsSkipped() {
		consumer.consume(List.of(Updates.withMessage(1, Updates.message(100, 42, 1).build())));
		assertThat(handled).isEmpty();
	}
}
