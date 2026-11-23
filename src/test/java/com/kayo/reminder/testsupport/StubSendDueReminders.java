package com.kayo.reminder.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kayo.reminder.application.service.SendDueRemindersService;
import java.util.function.IntSupplier;

public final class StubSendDueReminders {

	private StubSendDueReminders() {
	}

	public static SendDueRemindersService of(IntSupplier run) {
		SendDueRemindersService stub = mock(SendDueRemindersService.class);
		when(stub.sendDueReminders()).thenAnswer(call -> run.getAsInt());
		return stub;
	}
}
