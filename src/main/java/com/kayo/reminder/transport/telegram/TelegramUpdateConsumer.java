package com.kayo.reminder.transport.telegram;

import com.kayo.reminder.application.component.CorrelationId;
import com.kayo.reminder.application.service.IncomingCommandService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

public class TelegramUpdateConsumer implements LongPollingUpdateConsumer {

	private static final Logger log = LoggerFactory.getLogger(TelegramUpdateConsumer.class);

	private final UpdateAllowlist allowlist;
	private final TelegramUpdateMapper mapper;
	private final IncomingCommandService commands;

	public TelegramUpdateConsumer(UpdateAllowlist allowlist, TelegramUpdateMapper mapper,
			IncomingCommandService commands) {
		this.allowlist = allowlist;
		this.mapper = mapper;
		this.commands = commands;
	}

	@Override
	public void consume(List<Update> updates) {
		updates.forEach(this::consumeOne);
	}

	private void consumeOne(Update update) {
		try (CorrelationId scope = CorrelationId.open()) {
			handle(update);
		}
	}

	private void handle(Update update) {
		try {
			process(update);
		}
		catch (Exception failure) {
			log.error("Failed to process update id={}", Optional.ofNullable(update).map(Update::getUpdateId).orElse(null),
					failure);
		}
	}

	private void process(Update update) {
		Optional.ofNullable(update).filter(allowlist::isAllowed).ifPresent(allowed -> mapper.toCommand(allowed)
				.ifPresentOrElse(commands::handle,
						() -> log.debug("Skipping update id={} of an unsupported kind", allowed.getUpdateId())));
	}
}
