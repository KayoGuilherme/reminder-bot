package com.kayo.reminder.testsupport;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.kayo.reminder.application.dto.request.IncomingCommandRequest;
import com.kayo.reminder.application.service.IncomingCommandService;
import java.util.function.Consumer;

public final class StubIncomingCommands {

	private StubIncomingCommands() {
	}

	public static IncomingCommandService of(Consumer<IncomingCommandRequest> handler) {
		IncomingCommandService stub = mock(IncomingCommandService.class);
		doAnswer(call -> {
			handler.accept(call.getArgument(0));
			return null;
		}).when(stub).handle(org.mockito.ArgumentMatchers.any());
		return stub;
	}
}
