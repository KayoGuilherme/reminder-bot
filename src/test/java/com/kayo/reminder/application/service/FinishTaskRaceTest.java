package com.kayo.reminder.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.AbstractIntegrationTest;
import com.kayo.reminder.application.component.TaskFactory;
import com.kayo.reminder.application.dto.request.CreateTaskRequest;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.domain.exceptions.TaskActionException;
import com.kayo.reminder.domain.models.enums.TaskFailure;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FinishTaskRaceTest extends AbstractIntegrationTest {

	private static final int ROUNDS = 20;

	@Autowired
	FinishTaskService finish;

	@Autowired
	TaskService tasks;

	@Autowired
	TaskFactory factory;

	private UUID pendingTask() {
		TaskResponse task = factory.create(new CreateTaskRequest(UUID.randomUUID(), 42L, "race", Urgency.LOW, null,
				Instant.now().plus(1, ChronoUnit.HOURS), "en"));
		tasks.save(task);
		return task.id();
	}

	private Callable<String> attempt(CountDownLatch start, Callable<Object> action) {
		return () -> {
			start.await();
			try {
				action.call();
				return "won";
			}
			catch (TaskActionException refused) {
				return refused.failure().name();
			}
		};
	}

	@Test
	void completeAndCancelRacingLeaveExactlyOneWinner() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			IntStream.range(0, ROUNDS).forEach(round -> raceOnce(pool));
		}
		finally {
			pool.shutdownNow();
		}
	}

	private void raceOnce(ExecutorService pool) {
		try {
			UUID id = pendingTask();
			CountDownLatch start = new CountDownLatch(1);
			Future<String> complete = pool.submit(attempt(start, () -> finish.complete(id.toString())));
			Future<String> cancel = pool.submit(attempt(start, () -> finish.cancel(id.toString())));
			start.countDown();
			List<String> outcomes = List.of(complete.get(), cancel.get());

			assertThat(outcomes).containsExactlyInAnyOrder("won", TaskFailure.TASK_NOT_PENDING.name());
			TaskStatus winner = outcomes.get(0).equals("won") ? TaskStatus.DONE : TaskStatus.CANCELED;
			assertThat(tasks.findById(id)).get().extracting(TaskResponse::status).isEqualTo(winner);
		}
		catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
	}
}
