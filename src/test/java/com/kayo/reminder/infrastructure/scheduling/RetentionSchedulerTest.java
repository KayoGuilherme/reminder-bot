package com.kayo.reminder.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.kayo.reminder.application.port.in.CleanUpFinishedTasksUseCase;
import com.kayo.reminder.config.AppProperties;
import com.kayo.reminder.infrastructure.logging.CorrelationId;
import com.kayo.reminder.testsupport.LogCapture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class RetentionSchedulerTest {

	private static final String[] VALID = {
			"TELEGRAM_BOT_TOKEN=bot-token-secret",
			"TELEGRAM_ALLOWED_USER_ID=12345",
			"GROQ_API_KEY=groq-secret",
			"GEMINI_API_KEY=gemini-secret"
	};

	private ApplicationContextRunner runner(CleanUpFinishedTasksUseCase useCase, String... overrides) {
		return new ApplicationContextRunner()
				.withInitializer(new ConfigDataApplicationContextInitializer())
				.withUserConfiguration(RetentionConfiguration.class, Props.class)
				.withBean(CleanUpFinishedTasksUseCase.class, () -> useCase)
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

	@Test
	void runsDailyAtThreeInTheDisplayTimezone() {
		runner(() -> 0, "APP_TIMEZONE=America/Fortaleza").run(context -> {
			assertThat(context).hasSingleBean(RetentionScheduler.class);
			var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
			assertThat(tasks).hasSize(1);
			CronTask task = (CronTask) tasks.iterator().next().getTask();
			assertThat(task.getExpression()).isEqualTo("0 0 3 * * *");
			CronTrigger trigger = (CronTrigger) task.getTrigger();
			// 00:00 UTC is 21:00 the evening before in Fortaleza, so the next 03:00 there is 06:00 UTC.
			Clock now = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
			assertThat(trigger.nextExecution(new SimpleTriggerContext(now)))
					.isEqualTo(Instant.parse("2026-11-22T06:00:00Z"));
		});
	}

	@Test
	void doesNotExistWhenDisabled() {
		runner(() -> 0, "APP_RETENTION_ENABLED=false").run(context -> {
			assertThat(context).doesNotHaveBean(RetentionScheduler.class);
			assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
		});
	}

	@Test
	void aRunLogsOnlyTheCountWithACorrelationId() {
		try (LogCapture logs = new LogCapture(RetentionScheduler.class)) {
			new RetentionScheduler(() -> 3).run();

			assertThat(logs.events(Level.INFO)).hasSize(1);
			assertThat(logs.events(Level.INFO).get(0).getFormattedMessage()).contains("3");
			assertThat(logs.events(Level.INFO).get(0).getMDCPropertyMap()).containsKey(CorrelationId.KEY);
		}
	}

	@Test
	void aFailedRunIsLoggedAndTheNextRunStillWorks() {
		AtomicInteger calls = new AtomicInteger();
		RetentionScheduler scheduler = new RetentionScheduler(() -> {
			if (calls.incrementAndGet() == 1) {
				throw new IllegalStateException("database down");
			}
			return 2;
		});
		try (LogCapture logs = new LogCapture(RetentionScheduler.class)) {
			scheduler.run();
			scheduler.run();

			assertThat(logs.events(Level.ERROR)).hasSize(1);
			assertThat(logs.events(Level.ERROR).get(0).getMDCPropertyMap()).containsKey(CorrelationId.KEY);
			assertThat(logs.events(Level.INFO)).hasSize(1);
			assertThat(calls).hasValue(2);
		}
	}
}
