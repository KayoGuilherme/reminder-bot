package com.kayo.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Telegram library types may only be used inside infrastructure.telegram. */
class TelegramBoundaryTest {

	private static final Path MAIN = Path.of("src/main/java");
	private static final Path ALLOWED = MAIN.resolve("com/kayo/reminder/infrastructure/telegram");

	@Test
	void onlyTheTelegramAdapterImportsTelegramClasses() throws IOException {
		List<String> offenders;
		try (Stream<Path> files = Files.walk(MAIN)) {
			offenders = files
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> !p.startsWith(ALLOWED))
					.filter(TelegramBoundaryTest::mentionsTelegramLibrary)
					.map(Path::toString)
					.toList();
		}
		assertThat(offenders).as("files outside infrastructure.telegram that use org.telegram").isEmpty();
	}

	@Test
	void theScanActuallyFindsTheAdapterSources() throws IOException {
		try (Stream<Path> files = Files.walk(ALLOWED)) {
			assertThat(files.filter(p -> p.toString().endsWith(".java"))
					.filter(TelegramBoundaryTest::mentionsTelegramLibrary).count()).isGreaterThan(0);
		}
	}

	private static boolean mentionsTelegramLibrary(Path file) {
		try {
			return Files.readString(file).contains("org.telegram");
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
