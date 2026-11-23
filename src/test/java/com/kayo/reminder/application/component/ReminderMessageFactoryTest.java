package com.kayo.reminder.application.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.kayo.reminder.application.dto.response.ReminderDecisionResponse;
import com.kayo.reminder.application.dto.response.TaskResponse;
import com.kayo.reminder.application.service.ReminderPolicy;
import com.kayo.reminder.domain.models.enums.TaskStatus;
import com.kayo.reminder.domain.models.enums.Urgency;
import com.kayo.reminder.testsupport.TestMessages;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReminderMessageFactoryTest {

	private static final Instant DEADLINE = Instant.parse("2026-11-22T15:00:00Z");

	private static TaskResponse task(String title, Urgency urgency) {
		return task(title, urgency, "en");
	}

	private static TaskResponse task(String title, Urgency urgency, String language) {
		return new TaskResponse(UUID.randomUUID(), 1L, title, urgency, TaskStatus.PENDING, null, DEADLINE, (short) 0, null,
				DEADLINE.minus(Duration.ofHours(1)), DEADLINE.minus(Duration.ofHours(1)), language);
	}

	private static ReminderMessageFactory factory(String language) {
		return new ReminderMessageFactory(TestMessages.of(language));
	}

	private static String render(String language, String title, Urgency urgency, int stage, Duration timeLeft) {
		return factory(language).create(task(title, urgency, language), ReminderDecisionResponse.scheduled(stage),
				DEADLINE.minus(timeLeft));
	}

	static Stream<Arguments> everyCombination() {
		ReminderPolicy policy = ReminderPolicy.defaults();
		return Stream.of("en", "pt-BR").flatMap(language -> Stream.of(Urgency.values())
				.flatMap(urgency -> IntStream.rangeClosed(1, urgency == Urgency.HIGH ? 3 : policy.finalStage(urgency))
						.mapToObj(stage -> Arguments.of(language, urgency, stage))));
	}

	@ParameterizedTest(name = "{0} {1} stage {2}")
	@MethodSource("everyCombination")
	void everyUrgencyAndStageRendersInBothLanguagesWithTitleAndMinutes(String language, Urgency urgency, int stage) {
		String text = render(language, "Send the report", urgency, stage, Duration.ofMinutes(10));

		assertThat(text).contains("Send the report").contains("10");
	}

	@Test
	void englishStageOneTextsMatchTheReferenceWording() {
		assertThat(render("en", "X", Urgency.LOW, 1, Duration.ofMinutes(10)))
				.isEqualTo("Hey, 10 minutes left for X, whenever you're ready.");
		assertThat(render("en", "X", Urgency.MEDIUM, 1, Duration.ofMinutes(10)))
				.isEqualTo("X is due in 10 minutes. Time to start.");
		assertThat(render("en", "X", Urgency.HIGH, 1, Duration.ofMinutes(10)))
				.isEqualTo("X is due in 10 MINUTES. Drop everything and do it now.");
	}

	@Test
	void portugueseIsInPortuguese() {
		assertThat(render("pt-BR", "Relatório", Urgency.HIGH, 1, Duration.ofMinutes(10)))
				.isEqualTo("Relatório vence em 10 MINUTOS. Largue tudo e faça isso agora.");
	}

	@Test
	void aPortugueseTaskIsRemindedInPortugueseWhateverTheAppLanguage() {
		TaskResponse task = new TaskResponse(UUID.randomUUID(), 1L, "Relatório", Urgency.HIGH, TaskStatus.PENDING, null,
				DEADLINE, (short) 0, null, DEADLINE.minus(Duration.ofHours(1)), DEADLINE.minus(Duration.ofHours(1)), "pt-BR");

		assertThat(factory("en").create(task, ReminderDecisionResponse.scheduled(1), DEADLINE.minus(Duration.ofMinutes(10))))
				.isEqualTo("Relatório vence em 10 MINUTOS. Largue tudo e faça isso agora.");
		assertThat(factory("en").buttons(task).get(0)).extracting(b -> b.text())
				.containsExactly("Feito ✅", "Adiar 10 min");
	}

	@Test
	void highStagesGetMoreForcefulInOrder() {
		String first = render("en", "X", Urgency.HIGH, 1, Duration.ofMinutes(10));
		String second = render("en", "X", Urgency.HIGH, 2, Duration.ofMinutes(5));
		String third = render("en", "X", Urgency.HIGH, 3, Duration.ZERO);

		assertThat(shoutedWords(first)).isLessThan(shoutedWords(second));
		assertThat(shoutedWords(second)).isLessThan(shoutedWords(third));
	}

	private static long shoutedWords(String text) {
		return Stream.of(text.split("[^\\p{L}]+")).filter(w -> w.length() > 1 && w.equals(w.toUpperCase())).count();
	}

	@Test
	void bothLanguagesHaveTheSameReminderKeys() throws IOException {
		assertThat(reminderKeys("/messages_pt_BR.properties")).isEqualTo(reminderKeys("/messages.properties"));
		assertThat(reminderKeys("/messages.properties")).hasSize(18).contains("reminder.HIGH.overdue.1",
				"reminder.HIGH.overdue.2", "reminder.HIGH.overdue.3", "reminder.HIGH.final", "reminder.done-button",
				"reminder.snooze-button");
	}

	private static Set<String> reminderKeys(String resource) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = ReminderMessageFactoryTest.class.getResourceAsStream(resource);
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties.stringPropertyNames().stream().filter(k -> k.startsWith("reminder."))
				.collect(Collectors.toCollection(TreeSet::new));
	}

	@Test
	void minutesAreRoundedUp() {
		assertThat(render("en", "X", Urgency.LOW, 1, Duration.ofMinutes(9).plusSeconds(40))).contains("10 minutes");
		assertThat(render("en", "X", Urgency.LOW, 1, Duration.ofMinutes(7))).contains("7 minutes");
		assertThat(render("en", "X", Urgency.LOW, 1, Duration.ofSeconds(30))).contains("1 minute ");
	}

	@Test
	void overdueShowsZeroNeverNegative() {
		assertThat(render("en", "X", Urgency.HIGH, 3, Duration.ofMinutes(3).negated())).contains("0 MINUTES")
				.doesNotContain("-");
		assertThat(render("en", "X", Urgency.MEDIUM, 2, Duration.ZERO)).contains("0 minutes");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Fix {0} it's \"ok\"", "100% {1} done", "a'b''c", "{title} and {minutes}" })
	void titlesAreShownLiterally(String title) {
		assertThat(render("en", title, Urgency.MEDIUM, 1, Duration.ofMinutes(10))).startsWith(title + " is due in");
	}

	private static String overdue(String language, String title, int repeat, Duration overdueBy) {
		return factory(language).create(task(title, Urgency.HIGH, language), ReminderDecisionResponse.overdue(3 + repeat, repeat),
				DEADLINE.plus(overdueBy));
	}

	private static String finalMessage(String language, String title, Duration overdueBy) {
		return factory(language).create(task(title, Urgency.HIGH, language), ReminderDecisionResponse.finalStop(14, 10),
				DEADLINE.plus(overdueBy));
	}

	@ParameterizedTest(name = "{0} repeat {1}")
	@MethodSource("everyRepeat")
	void everyOverdueRepeatRendersInBothLanguagesWithTitleAndMinutesOverdue(String language, int repeat) {
		assertThat(overdue(language, "Send the report", repeat, Duration.ofMinutes(12)))
				.contains("Send the report").contains("12");
	}

	static Stream<Arguments> everyRepeat() {
		return Stream.of("en", "pt-BR").flatMap(language -> IntStream.rangeClosed(1, 25)
				.mapToObj(repeat -> Arguments.of(language, repeat)));
	}

	@Test
	void overdueTextsGrowMoreForcefulByBand() {
		long first = shoutedWords(overdue("en", "X", 1, Duration.ofMinutes(2)));
		long second = shoutedWords(overdue("en", "X", 4, Duration.ofMinutes(8)));
		long third = shoutedWords(overdue("en", "X", 7, Duration.ofMinutes(14)));

		assertThat(first).isLessThan(second);
		assertThat(second).isLessThan(third);
	}

	@Test
	void overdueTextsStayInTheSameBandWithinIt() {
		assertThat(overdue("en", "X", 1, Duration.ofMinutes(2))).isEqualTo(overdue("en", "X", 3, Duration.ofMinutes(2)));
		assertThat(overdue("en", "X", 4, Duration.ofMinutes(2))).isEqualTo(overdue("en", "X", 6, Duration.ofMinutes(2)));
	}

	@Test
	void repeatsBeyondTheLastBandReuseTheMostIntenseText() {
		assertThat(overdue("en", "X", 25, Duration.ofMinutes(50))).isEqualTo(overdue("en", "X", 7, Duration.ofMinutes(50)));
	}

	@Test
	void overdueMinutesAreWholeMinutesRoundedDown() {
		assertThat(overdue("en", "X", 1, Duration.ofMinutes(7).plusSeconds(30))).contains("7 MINUTES");
		assertThat(overdue("en", "X", 1, Duration.ofSeconds(30))).contains("0 MINUTES").doesNotContain("-");
		assertThat(overdue("en", "X", 1, Duration.ofMinutes(1).plusSeconds(5))).contains("1 MINUTE ");
	}

	@Test
	void theFinalMessageSaysTheBotStopsReminding() {
		assertThat(finalMessage("en", "X", Duration.ofMinutes(24))).contains("stop reminding").contains("24");
		assertThat(finalMessage("pt-BR", "Relatório", Duration.ofMinutes(24))).contains("Relatório").contains("24");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Fix {0} it's \"ok\"", "100% {1} done", "a'b''c", "{title} and {minutes}" })
	void overdueAndFinalTitlesAreShownLiterally(String title) {
		assertThat(overdue("en", title, 1, Duration.ofMinutes(2))).startsWith(title + " is ");
		assertThat(finalMessage("en", title, Duration.ofMinutes(2))).startsWith(title + " is ");
	}

	@Test
	void buttonsAreDoneAndSnoozeWithTheTaskIdInTheCallbackData() {
		TaskResponse task = task("X", Urgency.HIGH);

		var buttons = factory("en").buttons(task);

		assertThat(buttons).hasSize(1);
		assertThat(buttons.get(0)).extracting(b -> b.text()).containsExactly("Done \u2705", "Snooze 10 min");
		assertThat(buttons.get(0)).extracting(b -> b.callbackData())
				.containsExactly("task:done:" + task.id(), "task:snooze:" + task.id());
		assertThat(factory("en").buttons(task("X", Urgency.HIGH, "pt-BR")).get(0)).extracting(b -> b.text())
				.containsExactly("Feito \u2705", "Adiar 10 min");
	}
}
