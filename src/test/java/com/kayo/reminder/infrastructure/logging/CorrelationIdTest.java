package com.kayo.reminder.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationIdTest {

	@Test
	void putsAShortIdInTheLoggingContextAndRemovesItOnClose() {
		try (CorrelationId scope = CorrelationId.open()) {
			assertThat(scope.id()).hasSize(8);
			assertThat(MDC.get(CorrelationId.KEY)).isEqualTo(scope.id());
		}
		assertThat(MDC.get(CorrelationId.KEY)).isNull();
	}

	@Test
	void eachScopeGetsItsOwnId() {
		String first;
		String second;
		try (CorrelationId scope = CorrelationId.open()) {
			first = scope.id();
		}
		try (CorrelationId scope = CorrelationId.open()) {
			second = scope.id();
		}
		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void anInnerScopeRestoresTheOuterId() {
		try (CorrelationId outer = CorrelationId.open()) {
			try (CorrelationId inner = CorrelationId.open()) {
				assertThat(MDC.get(CorrelationId.KEY)).isEqualTo(inner.id());
			}
			assertThat(MDC.get(CorrelationId.KEY)).isEqualTo(outer.id());
		}
		assertThat(MDC.get(CorrelationId.KEY)).isNull();
	}

	@Test
	void theIdIsClearedEvenWhenTheScopeIsLeftByAnException() {
		try {
			try (CorrelationId scope = CorrelationId.open()) {
				throw new IllegalStateException("boom");
			}
		}
		catch (IllegalStateException expected) {
			// expected
		}
		assertThat(MDC.get(CorrelationId.KEY)).isNull();
	}
}
