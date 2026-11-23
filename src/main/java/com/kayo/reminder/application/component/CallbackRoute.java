package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import java.util.Optional;
import java.util.UUID;

public class CallbackRoute {

	@FunctionalInterface
	public interface Action {

		void run(long chatId, Integer messageId, UUID taskId);
	}

	private final String prefix;
	private final Action action;

	public CallbackRoute(String prefix, Action action) {
		this.prefix = prefix;
		this.action = action;
	}

	public boolean accepts(String callbackData) {
		return callbackData.startsWith(prefix);
	}

	public void run(IncomingCommandRequest command) {
		taskId(command.callbackData()).ifPresent(id -> action.run(command.chatId(), command.messageId(), id));
	}

	private Optional<UUID> taskId(String callbackData) {
		try {
			return Optional.of(UUID.fromString(callbackData.substring(prefix.length())));
		}
		catch (IllegalArgumentException invalid) {
			return Optional.empty();
		}
	}
}
