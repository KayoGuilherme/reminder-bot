package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.in.HandleReminderActionUseCase;
import com.kayo.reminder.application.port.in.ListTasksUseCase;
import com.kayo.reminder.domain.Task;
import com.kayo.reminder.domain.TaskStatus;
import com.kayo.reminder.domain.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskRepository;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.RecordingSender;
import com.kayo.reminder.testsupport.TestMessages;
import com.kayo.reminder.testsupport.TestProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListTasksServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
	private static final long CHAT = 7L;

	private final InMemoryTaskRepository repository = new InMemoryTaskRepository();
	private final RecordingSender sender = new RecordingSender();

	private ListTasksService service(String language, ZoneId zone) {
		return new ListTasksService(repository, sender, TestMessages.of(language),
				TestProperties.of(zone, language), new MutableClock(NOW));
	}

	private ListTasksService service() {
		return service("en", ZoneId.of("UTC"));
	}

	private Task add(long chat, String title, Urgency urgency, TaskStatus status, Duration fromNow) {
		return repository.add(chat, title, urgency, status, NOW.plus(fromNow));
	}

	private static String text(String key, Object... args) {
		return TestMessages.text("en", key, args);
	}

	@Test
	void tasksAreListedEarliestDeadlineFirstOneMessageEach() {
		add(CHAT, "evening", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(3));
		add(CHAT, "morning", Urgency.LOW, TaskStatus.PENDING, Duration.ofMinutes(30));
		add(CHAT, "noon", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(1));

		service().list(CHAT);

		assertThat(sender.withButtons).hasSize(3);
		assertThat(sender.withButtons).extracting(m -> m.text().lines().findFirst().orElseThrow())
				.satisfiesExactly(a -> assertThat(a).endsWith("morning"), b -> assertThat(b).endsWith("noon"),
						c -> assertThat(c).endsWith("evening"));
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void overdueTasksComeFirstAndAreFlagged() {
		add(CHAT, "tomorrow", Urgency.MEDIUM, TaskStatus.PENDING, Duration.ofDays(1));
		add(CHAT, "late", Urgency.MEDIUM, TaskStatus.PENDING, Duration.ofMinutes(-30));

		service().list(CHAT);

		assertThat(sender.withButtons.get(0).text()).contains("late").contains("Overdue");
		assertThat(sender.withButtons.get(1).text()).contains("tomorrow").doesNotContain("Overdue");
	}

	@Test
	void onlyPendingTasksOfTheChatAreListed() {
		add(CHAT, "pending", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(1));
		add(CHAT, "finished", Urgency.LOW, TaskStatus.DONE, Duration.ofHours(1));
		add(CHAT, "dropped", Urgency.LOW, TaskStatus.CANCELED, Duration.ofHours(1));
		add(999L, "someone else's", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(1));

		service().list(CHAT);

		assertThat(sender.withButtons).hasSize(1);
		assertThat(sender.withButtons.get(0).text()).contains("pending");
	}

	@Test
	void deadlinesAreShownInTheDisplayTimezoneNotUtc() {
		repository.add(CHAT, "report", Urgency.HIGH, TaskStatus.PENDING, Instant.parse("2026-11-22T21:00:00Z"));

		service("en", ZoneId.of("America/Fortaleza")).list(CHAT);

		assertThat(sender.withButtons.get(0).text()).contains("2026-11-22 18:00").doesNotContain("21:00");
	}

	@Test
	void eachUrgencyHasItsOwnMarkerAndLabel() {
		add(CHAT, "a", Urgency.HIGH, TaskStatus.PENDING, Duration.ofHours(1));
		add(CHAT, "b", Urgency.MEDIUM, TaskStatus.PENDING, Duration.ofHours(2));
		add(CHAT, "c", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(3));

		service().list(CHAT);

		List<String> heads = sender.withButtons.stream().map(m -> m.text().lines().findFirst().orElseThrow()).toList();
		assertThat(heads).containsExactly("\uD83D\uDD34 High \u00b7 a", "\uD83D\uDFE1 Medium \u00b7 b",
				"\uD83D\uDFE2 Low \u00b7 c");
	}

	@Test
	void titlesWithMarkupCharactersAreSentUnchanged() {
		add(CHAT, "buy <milk> *now* _please_ {0}", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(1));

		service().list(CHAT);

		assertThat(sender.withButtons.get(0).text()).contains("buy <milk> *now* _please_ {0}");
	}

	@Test
	void everyMessageHasDoneAndCancelButtonsIdentifyingItsTask() {
		Task task = add(CHAT, "report", Urgency.LOW, TaskStatus.PENDING, Duration.ofHours(1));

		service().list(CHAT);

		var rows = sender.withButtons.get(0).buttons();
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).hasSize(2);
		assertThat(rows.get(0).get(0).text()).isEqualTo(text("tasks.done-button"));
		assertThat(rows.get(0).get(0).callbackData()).isEqualTo(HandleReminderActionUseCase.DONE_PREFIX + task.id());
		assertThat(rows.get(0).get(1).text()).isEqualTo(text("tasks.cancel-button"));
		assertThat(rows.get(0).get(1).callbackData())
				.isEqualTo(HandleReminderActionUseCase.CANCEL_PREFIX + task.id());
		assertThat(rows.get(0).get(1).callbackData().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(64);
	}

	@Test
	void anEmptyListGetsOneFriendlyMessage() {
		service().list(CHAT);

		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("tasks.empty"));
		assertThat(sender.withButtons).isEmpty();
	}

	@Test
	void whenEverythingIsFinishedTheSameFriendlyMessageIsSent() {
		add(CHAT, "x", Urgency.LOW, TaskStatus.DONE, Duration.ofHours(1));
		add(CHAT, "y", Urgency.LOW, TaskStatus.CANCELED, Duration.ofHours(1));

		service().list(CHAT);

		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("tasks.empty"));
	}

	@Test
	void moreThanTheLimitShowsTheEarliestOnesAndSaysHowManyAreLeft() {
		for (int i = 25; i >= 1; i--) {
			add(CHAT, "task " + i, Urgency.LOW, TaskStatus.PENDING, Duration.ofMinutes(i));
		}

		service().list(CHAT);

		assertThat(sender.withButtons).hasSize(ListTasksUseCase.LIMIT);
		assertThat(sender.withButtons.get(0).text()).contains("task 1\n");
		assertThat(sender.withButtons.get(19).text()).contains("task 20\n");
		assertThat(sender.texts).extracting(t -> t.text()).containsExactly(text("tasks.more", 5L));
	}

	@Test
	void exactlyTheLimitHasNoMoreMessage() {
		for (int i = 1; i <= ListTasksUseCase.LIMIT; i++) {
			add(CHAT, "task " + i, Urgency.LOW, TaskStatus.PENDING, Duration.ofMinutes(i));
		}

		service().list(CHAT);

		assertThat(sender.withButtons).hasSize(ListTasksUseCase.LIMIT);
		assertThat(sender.texts).isEmpty();
	}

	@Test
	void textsAreInThePortugueseWhenConfigured() {
		service("pt-BR", ZoneId.of("UTC")).list(CHAT);
		add(CHAT, "relatorio", Urgency.HIGH, TaskStatus.PENDING, Duration.ofHours(1));
		service("pt-BR", ZoneId.of("UTC")).list(CHAT);

		assertThat(sender.texts.get(0).text()).isEqualTo(TestMessages.text("pt-BR", "tasks.empty"));
		assertThat(sender.withButtons.get(0).text()).contains("Alta").contains("Prazo:");
		assertThat(sender.withButtons.get(0).buttons().get(0).get(1).text()).startsWith("Cancelar");
	}
}
