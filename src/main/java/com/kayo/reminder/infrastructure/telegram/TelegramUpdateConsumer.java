package com.kayo.reminder.infrastructure.telegram;

import com.kayo.reminder.application.port.in.HandleIncomingCommandUseCase;
import com.kayo.reminder.application.port.in.IncomingCommand;
import com.kayo.reminder.infrastructure.logging.CorrelationId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Entry point of long polling. Updates are handled in order; anything thrown while handling one is logged
 * (update id only, never the content), and every log line of an update carries one correlation id so that the next update and the polling loop are unaffected.
 */
class TelegramUpdateConsumer implements LongPollingUpdateConsumer {

	private static final Logger log = LoggerFactory.getLogger(TelegramUpdateConsumer.class);

	private final UpdateAllowlist allowlist;
	private final TelegramUpdateMapper mapper;
	private final HandleIncomingCommandUseCase useCase;

	TelegramUpdateConsumer(UpdateAllowlist allowlist, TelegramUpdateMapper mapper,
			HandleIncomingCommandUseCase useCase) {
		this.allowlist = allowlist;
		this.mapper = mapper;
		this.useCase = useCase;
	}

	@Override
	public void consume(List<Update> updates) {
		for (Update update : updates) {
			try (CorrelationId scope = CorrelationId.open()) {
				try {
					process(update);
				}
				catch (Exception ex) {
					log.error("Failed to process update id={}", update == null ? null : update.getUpdateId(), ex);
				}
			}
		}
	}

	private void process(Update update) {
		if (update == null || !allowlist.isAllowed(update)) {
			return;
		}
		Optional<IncomingCommand> command = mapper.toCommand(update);
		if (command.isEmpty()) {
			log.debug("Skipping update id={} of an unsupported kind", update.getUpdateId());
			return;
		}
		useCase.handle(command.get());
	}
}
