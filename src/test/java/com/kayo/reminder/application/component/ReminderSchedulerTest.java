package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.service.SendDueRemindersService;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.config.SchedulingConfiguration;
import com.kayo.reminder.testsupport.LogCapture;
import com.kayo.reminder.testsupport.StubSendDueReminders;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

class ReminderSchedulerTest {

	private static final String[] VALID = {
			"TELEGRAM_BOT_TOKEN=bot-token-secret",
			"TELEGRAM_ALLOWED_USER_ID=12345",
			"GROQ_API_KEY=groq-secret",
			"GEMINI_API_KEY=gemini-secret"
	};

	private final AtomicInteger runs = new AtomicInteger();

	private ApplicationContextRunner runner(IntSupplier run, String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(SchedulingConfiguration.class, Props.class)
				.withBean(SendDueRemindersService.class, () -> StubSendDueReminders.of(run))
				.withPropertyValues(VALID)
				.withPropertyValues(overrides);
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class Props {
		@Bean
		String marker() {
			return "";
		}
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
			Thread.sleep(25);
		}
		assertThat(condition.getAsBoolean()).isTrue();
	}

	@Test
	void isActiveByDefaultWithAThirtySecondDelay() {
		runner(() -> 0).run(context -> {
			assertThat(context).hasSingleBean(ReminderScheduler.class);
			var task = (FixedDelayTask) context.getBean(ScheduledAnnotationBeanPostProcessor.class)
					.getScheduledTasks().iterator().next().getTask();
			assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(30));
		});
	}

	@Test
	void theIntervalComesFromTheSetting() {
		runner(() -> 0, "app.scheduler.interval=1m").run(context -> {
			var task = (FixedDelayTask) context
					.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().iterator().next()
					.getTask();
			assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(1));
		});
	}

	@Test
	void doesNotExistWhenDisabled() {
		runner(() -> 0, "app.scheduler.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(ReminderScheduler.class);
			assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
		});
	}

	@Test
	void doesNotExistWithoutTheReminderService() {
		new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(SchedulingConfiguration.class, Props.class).withPropertyValues(VALID)
				.run(context -> assertThat(context).doesNotHaveBean(ReminderScheduler.class));
	}

	@Test
	void runsRepeatedlyAndSurvivesARunThatThrows() {
		runner(() -> {
			if (runs.incrementAndGet() == 1) {
				throw new IllegalStateException("boom");
			}
			return 0;
		}, "app.scheduler.interval=20ms").run(context -> await(() -> runs.get() >= 3));
	}

	@Test
	void eachRunHasItsOwnCorrelationIdAndNothingLeaksAfterwards() {
		var ids = new java.util.concurrent.CopyOnWriteArrayList<String>();
		ReminderScheduler scheduler = new ReminderScheduler(StubSendDueReminders.of(() -> {
			ids.add(org.slf4j.MDC.get(com.kayo.reminder.application.component.CorrelationId.KEY));
			return 0;
		}));

		scheduler.tick();
		scheduler.tick();

		assertThat(ids).hasSize(2).doesNotContainNull();
		assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
		assertThat(org.slf4j.MDC.get(com.kayo.reminder.application.component.CorrelationId.KEY)).isNull();
	}

	@Test
	void aFailedRunIsLoggedWithItsCorrelationId() {
		ReminderScheduler scheduler = new ReminderScheduler(StubSendDueReminders.of(() -> {
			throw new IllegalStateException("boom");
		}));
		try (var logs = new com.kayo.reminder.testsupport.LogCapture(ReminderScheduler.class)) {
			scheduler.tick();

			var errors = logs.events(ch.qos.logback.classic.Level.ERROR);
			assertThat(errors).hasSize(1);
			assertThat(errors.get(0).getMDCPropertyMap())
					.containsKey(com.kayo.reminder.application.component.CorrelationId.KEY);
		}
	}
}
