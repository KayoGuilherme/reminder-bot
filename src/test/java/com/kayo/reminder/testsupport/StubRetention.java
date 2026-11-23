package com.kayo.reminder.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kayo.reminder.application.service.RetentionService;
import java.util.function.IntSupplier;

public final class StubRetention {

	private StubRetention() {
	}

	public static RetentionService of(IntSupplier run) {
		RetentionService stub = mock(RetentionService.class);
		when(stub.deleteExpired()).thenAnswer(call -> run.getAsInt());
		return stub;
	}
}
