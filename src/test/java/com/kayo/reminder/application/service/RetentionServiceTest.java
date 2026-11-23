package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.config.RetentionProperties;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.InMemoryTaskService;
import com.kayo.reminder.testsupport.MutableClock;
import com.kayo.reminder.testsupport.TestProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RetentionServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");

	private final InMemoryTaskService repository = new InMemoryTaskService();
	private final MutableClock clock = new MutableClock(NOW);

	private RetentionService service(int days) {
		AppProperties base = TestProperties.of(ZoneId.of("UTC"), "en");
		AppProperties properties = new AppProperties(base.timezone(), base.language(), base.telegram(), base.groq(),
				base.gemini(), base.stt(), base.llm(), base.voice(), base.draft(), base.scheduler(), base.reminder(),
				new RetentionProperties(true, days), base.resilience(), base.shutdown());
		return new RetentionService(repository, properties, clock);
	}

	private TaskResponse task(TaskStatus status, Duration age) {
		TaskResponse added = repository.add(1L, "t", Urgency.LOW, status, NOW.plus(Duration.ofDays(3)));
		TaskResponse aged = new TaskResponse(added.id(), 1L, "t", Urgency.LOW, status, null, added.deadlineAt(), (short) 0, null,
				NOW.minus(age).minusSeconds(3600), NOW.minus(age), added.language());
		repository.tasks.put(aged.id(), aged);
		return aged;
	}

	@Test
	void oldDoneAndCanceledTasksAreDeleted() {
		TaskResponse done = task(TaskStatus.DONE, Duration.ofDays(31));
		TaskResponse canceled = task(TaskStatus.CANCELED, Duration.ofDays(45));

		assertThat(service(30).deleteExpired()).isEqualTo(2);

		assertThat(repository.tasks).doesNotContainKeys(done.id(), canceled.id());
	}

	@Test
	void recentFinishedTasksAndTheBoundaryAreKept() {
		TaskResponse recent = task(TaskStatus.DONE, Duration.ofDays(29));
		TaskResponse boundary = task(TaskStatus.CANCELED, Duration.ofDays(30));

		assertThat(service(30).deleteExpired()).isZero();

		assertThat(repository.tasks).containsKeys(recent.id(), boundary.id());
	}

	@Test
	void pendingTasksAreNeverDeletedHoweverOld() {
		TaskResponse pending = task(TaskStatus.PENDING, Duration.ofDays(200));

		assertThat(service(30).deleteExpired()).isZero();

		assertThat(repository.tasks).containsKey(pending.id());
	}

	@Test
	void theConfiguredNumberOfDaysIsHonored() {
		TaskResponse canceled = task(TaskStatus.CANCELED, Duration.ofDays(8));
		TaskResponse done = task(TaskStatus.DONE, Duration.ofDays(6));

		assertThat(service(7).deleteExpired()).isEqualTo(1);

		assertThat(repository.tasks).doesNotContainKey(canceled.id()).containsKey(done.id());
	}

	@Test
	void theAgeIsMeasuredWithTheInjectedClock() {
		TaskResponse done = task(TaskStatus.DONE, Duration.ofDays(29));
		RetentionService service = service(30);
		assertThat(service.deleteExpired()).isZero();

		clock.advance(Duration.ofDays(2));

		assertThat(service.deleteExpired()).isEqualTo(1);
		assertThat(repository.tasks).doesNotContainKey(done.id());
	}
}
