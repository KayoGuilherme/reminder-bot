package com.kayo.reminder.application.service;

import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.Urgency;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Drafts awaiting confirmation, in memory (a single instance is assumed; a restart forgets them). {@link #take} is
 * atomic, so of two concurrent presses exactly one gets the draft. Expired drafts are never handed out, and are
 * swept whenever a new one is added so the map cannot grow without a background thread.
 */
@Component
class DraftStore {

	private final ConcurrentMap<UUID, Draft> drafts = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration ttl;

	@Autowired
	DraftStore(Clock clock, AppProperties properties) {
		this(clock, properties.draft().ttl());
	}

	DraftStore(Clock clock, Duration ttl) {
		this.clock = clock;
		this.ttl = ttl;
	}

	Draft create(long chatId, String title, Urgency urgency, boolean urgencyAssumed, Instant startAt,
			Instant deadlineAt) {
		return create(chatId, title, urgency, urgencyAssumed, startAt, deadlineAt, null);
	}

	Draft create(long chatId, String title, Urgency urgency, boolean urgencyAssumed, Instant startAt,
			Instant deadlineAt, String language) {
		Instant now = clock.instant();
		drafts.values().removeIf(draft -> !now.isBefore(draft.expiresAt()));
		Draft draft = new Draft(UUID.randomUUID(), chatId, title, urgency, urgencyAssumed, startAt, deadlineAt,
				now.plus(ttl), language);
		drafts.put(draft.id(), draft);
		return draft;
	}

	/** Removes and returns the draft if it exists, belongs to {@code chatId} and has not expired. */
	Optional<Draft> take(UUID id, long chatId) {
		Draft[] taken = new Draft[1];
		drafts.computeIfPresent(id, (key, draft) -> {
			if (draft.chatId() != chatId) {
				return draft;
			}
			taken[0] = draft;
			return null;
		});
		Draft draft = taken[0];
		if (draft == null || !clock.instant().isBefore(draft.expiresAt())) {
			return Optional.empty();
		}
		return Optional.of(draft);
	}

	/** Puts a taken draft back (for example after a failed save) with its original expiry. */
	void restore(Draft draft) {
		drafts.putIfAbsent(draft.id(), draft);
	}

	int size() {
		return drafts.size();
	}
}
