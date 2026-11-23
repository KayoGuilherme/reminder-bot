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
	private static final org.slf4j.Logger HANDLER_LOG = org.slf4j.LoggerFactory.getLogger(TelegramUpdateConsumerTest.class);

	private final TelegramUpdateConsumer consumer = new TelegramUpdateConsumer(
			new UpdateAllowlist(42), new TelegramUpdateMapper(), command -> {
				HANDLER_LOG.info("handling update");
				if (command.text() != null && failOnText.contains(command.text())) {
					HANDLER_LOG.info("about to fail");
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

	private static String cid(ch.qos.logback.classic.spi.ILoggingEvent event) {
		return event.getMDCPropertyMap().get(com.kayo.reminder.infrastructure.logging.CorrelationId.KEY);
	}

	@Test
	void everyLogLineOfOneUpdateCarriesTheSameIdAndDifferentUpdatesDiffer() {
		failOnText.add("explode");
		try (LogCapture handler = new LogCapture(TelegramUpdateConsumerTest.class);
				LogCapture consumerLog = new LogCapture(TelegramUpdateConsumer.class)) {
			consumer.consume(List.of(Updates.text(7, 42, 100, "explode"), Updates.text(8, 42, 100, "after")));

			var first = handler.events().subList(0, 2);
			var second = handler.events().subList(2, 3);
			assertThat(first).extracting(e -> cid(e)).doesNotContainNull().containsOnly(cid(first.get(0)));
			assertThat(second).extracting(e -> cid(e)).doesNotContainNull();
			assertThat(cid(second.get(0))).isNotEqualTo(cid(first.get(0)));
			// the failure line of the first update carries the first update's id
			assertThat(consumerLog.events(Level.ERROR)).hasSize(1);
			assertThat(cid(consumerLog.events(Level.ERROR).get(0))).isEqualTo(cid(first.get(0)));
		}
	}

	@Test
	void theIdIsClearedWhenAnUpdateEnds() {
		failOnText.add("explode");
		consumer.consume(List.of(Updates.text(7, 42, 100, "explode"), Updates.text(8, 42, 100, "after")));

		assertThat(org.slf4j.MDC.get(com.kayo.reminder.infrastructure.logging.CorrelationId.KEY)).isNull();
		try (LogCapture later = new LogCapture(TelegramUpdateConsumerTest.class)) {
			HANDLER_LOG.info("unrelated");
			assertThat(cid(later.events().get(0))).isNull();
		}
	}
}
