package com.kayo.reminder.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.port.in.SendDueRemindersUseCase;
import com.kayo.reminder.config.AppProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/**
 * Stopping the context while a reminder run is sending must let the run finish and record, still with its
 * collaborators alive, and must not start another run.
 */
class GracefulShutdownTest {

	private static final String[] VALID = {
			"TELEGRAM_BOT_TOKEN=bot-token-secret",
			"TELEGRAM_ALLOWED_USER_ID=12345",
			"GROQ_API_KEY=groq-secret",
			"GEMINI_API_KEY=gemini-secret"
	};

	// Static because the context under test builds its beans from the static Config below; reset before each test.
	private static CountDownLatch started;
	private static CountDownLatch release;
	private static AtomicInteger runsStarted;
	private static AtomicBoolean runFinished;
	private static AtomicBoolean collaboratorAliveAtTheEnd;

	@BeforeEach
	void reset() {
		started = new CountDownLatch(1);
		release = new CountDownLatch(1);
		runsStarted = new AtomicInteger();
		runFinished = new AtomicBoolean();
		collaboratorAliveAtTheEnd = new AtomicBoolean();
	}

	/** Stands for the sender and the database: they must outlive the run in progress. */
	static class Collaborator implements DisposableBean {

		volatile boolean destroyed;

		@Override
		public void destroy() {
			destroyed = true;
		}
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class Config {

		@Bean
		Collaborator collaborator() {
			return new Collaborator();
		}

		@Bean
		SendDueRemindersUseCase useCase(Collaborator collaborator) {
			return () -> {
				runsStarted.incrementAndGet();
				started.countDown();
				try {
					release.await(10, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				collaboratorAliveAtTheEnd.set(!collaborator.destroyed);
				runFinished.set(true);
				return 1;
			};
		}
	}

	private ApplicationContextRunner runner(String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class, LifecycleAutoConfiguration.class))
				.withUserConfiguration(Config.class, SchedulingConfiguration.class)
				.withPropertyValues(VALID)
				.withPropertyValues("app.scheduler.interval=20ms", "APP_SHUTDOWN_TIMEOUT=5s")
				.withPropertyValues(overrides);
	}

	@Test
	void aRunInProgressFinishesBeforeTheContextStopsAndNoNewRunStarts() throws Exception {
		ExecutorService closer = Executors.newSingleThreadExecutor();
		try {
			runner().run(context -> {
				assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

				Future<?> closing = closer.submit(context::close);
				Thread.sleep(400);
				assertThat(closing.isDone()).as("close waits for the run in progress").isFalse();
				assertThat(runFinished).isFalse();

				release.countDown();
				closing.get(10, TimeUnit.SECONDS);

				assertThat(runFinished).isTrue();
				assertThat(collaboratorAliveAtTheEnd).as("its collaborators were still alive").isTrue();
				assertThat(runsStarted).as("no run started after the stop").hasValue(1);
			});
		}
		finally {
			closer.shutdownNow();
		}
	}

	@Test
	void anIdleContextClosesWithoutWaiting() throws Exception {
		release.countDown();
		runner("app.scheduler.interval=1h").run(context -> {
			long begin = System.nanoTime();
			context.close();
			assertThat((System.nanoTime() - begin) / 1_000_000).isLessThan(2_000);
		});
	}
}
