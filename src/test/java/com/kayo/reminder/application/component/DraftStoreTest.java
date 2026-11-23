package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.response.DraftResponse;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DraftStoreTest {

	private static final Instant START = Instant.parse("2026-11-22T13:00:00Z");
	private static final Duration TTL = Duration.ofMinutes(10);

	private final MutableClock clock = new MutableClock(START);
	private final DraftStore store = new DraftStore(clock, TTL);

	private DraftResponse draft(long chatId) {
		return store.create(chatId, "Pay rent", Urgency.HIGH, false, null, START.plusSeconds(3600), "en");
	}

	@Test
	void takeJustInsideTheTtlSucceeds() {
		DraftResponse draft = draft(5);
		clock.advance(TTL.minusSeconds(1));
		assertThat(store.take(draft.id(), 5)).contains(draft);
	}

	@Test
	void takeAfterTheTtlIsEmpty() {
		DraftResponse draft = draft(5);
		clock.advance(TTL.plusSeconds(1));
		assertThat(store.take(draft.id(), 5)).isEmpty();
	}

	@Test
	void secondTakeIsEmpty() {
		DraftResponse draft = draft(5);
		assertThat(store.take(draft.id(), 5)).isPresent();
		assertThat(store.take(draft.id(), 5)).isEmpty();
	}

	@Test
	void unknownIdIsEmpty() {
		assertThat(store.take(UUID.randomUUID(), 5)).isEmpty();
	}

	@Test
	void anotherChatCannotTakeAndDoesNotConsumeTheDraft() {
		DraftResponse draft = draft(5);
		assertThat(store.take(draft.id(), 6)).isEmpty();
		assertThat(store.take(draft.id(), 5)).isPresent();
	}

	@Test
	void restoreAllowsOneLaterTakeWithoutExtendingTheTtl() {
		DraftResponse draft = draft(5);
		clock.advance(Duration.ofMinutes(9));
		DraftResponse taken = store.take(draft.id(), 5).orElseThrow();
		store.restore(taken);

		clock.advance(Duration.ofSeconds(30));
		assertThat(store.take(draft.id(), 5)).isPresent();
		assertThat(store.take(draft.id(), 5)).isEmpty();

		store.restore(taken);
		clock.advance(Duration.ofMinutes(1));
		assertThat(store.take(draft.id(), 5)).isEmpty();
	}

	@Test
	void expiredDraftsAreSweptWhenANewOneIsAdded() {
		draft(5);
		draft(5);
		assertThat(store.size()).isEqualTo(2);
		clock.advance(TTL.plusSeconds(1));
		draft(5);
		assertThat(store.size()).isEqualTo(1);
	}

	@Test
	void draftsAreIndependent() {
		DraftResponse first = draft(5);
		DraftResponse second = draft(5);
		assertThat(first.id()).isNotEqualTo(second.id());
		assertThat(store.take(first.id(), 5)).contains(first);
		assertThat(store.take(second.id(), 5)).contains(second);
	}

	@Test
	void twoThreadsTakingTheSameDraftYieldExactlyOneWinner() throws Exception {
		for (int round = 0; round < 50; round++) {
			DraftResponse draft = draft(5);
			AtomicInteger winners = new AtomicInteger();
			CountDownLatch start = new CountDownLatch(1);
			var pool = Executors.newFixedThreadPool(2);
			for (int i = 0; i < 2; i++) {
				pool.submit(() -> {
					start.await();
					if (store.take(draft.id(), 5).isPresent()) {
						winners.incrementAndGet();
					}
					return null;
				});
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
			assertThat(winners).hasValue(1);
		}
	}
}
