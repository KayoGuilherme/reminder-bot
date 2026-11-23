package com.kayo.reminder.application.component;

import com.kayo.reminder.application.dto.response.DraftResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DraftStore {

	private final ConcurrentMap<UUID, DraftResponse> drafts = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration ttl;

	@Autowired
	public DraftStore(Clock clock, AppProperties properties) {
		this(clock, properties.draft().ttl());
	}

	public DraftStore(Clock clock, Duration ttl) {
		this.clock = clock;
		this.ttl = ttl;
	}

	public DraftResponse create(long chatId, String title, Urgency urgency, boolean urgencyAssumed, Instant startAt,
			Instant deadlineAt, String language) {
		Instant now = clock.instant();
		drafts.values().removeIf(draft -> !now.isBefore(draft.expiresAt()));
		DraftResponse draft = new DraftResponse(UUID.randomUUID(), chatId, title, urgency, urgencyAssumed, startAt, deadlineAt,
				now.plus(ttl), language);
		drafts.put(draft.id(), draft);
		return draft;
	}

	public Optional<DraftResponse> take(UUID id, long chatId) {
		AtomicReference<DraftResponse> taken = new AtomicReference<>();
		drafts.computeIfPresent(id, (key, draft) -> draft.chatId() == chatId ? claim(taken, draft) : draft);
		return Optional.ofNullable(taken.get()).filter(draft -> clock.instant().isBefore(draft.expiresAt()));
	}

	public void restore(DraftResponse draft) {
		drafts.putIfAbsent(draft.id(), draft);
	}

	public int size() {
		return drafts.size();
	}

	private static DraftResponse claim(AtomicReference<DraftResponse> taken, DraftResponse draft) {
		taken.set(draft);
		return null;
	}
}
