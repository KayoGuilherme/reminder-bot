package com.kayo.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TelegramBoundaryTest {

	private static final Path MAIN = Path.of("src/main/java");
	private static final Path ROOT = MAIN.resolve("com/kayo/reminder");
	private static final Path TRANSPORT = ROOT.resolve("transport");
	private static final List<Path> ALLOWED = List.of(TRANSPORT, ROOT.resolve("component/TelegramUpdateMapper.java"),
			ROOT.resolve("component/UpdateAllowlist.java"), ROOT.resolve("config/TelegramConfiguration.java"));

	@Test
	void onlyTheTelegramAdapterImportsTelegramClasses() throws IOException {
		List<String> offenders;
		try (Stream<Path> files = Files.walk(MAIN)) {
			offenders = files
					.filter(p -> p.toString().endsWith(".java"))
					.filter(p -> ALLOWED.stream().noneMatch(p::startsWith))
					.filter(TelegramBoundaryTest::mentionsTelegramLibrary)
					.map(Path::toString)
					.toList();
		}
		assertThat(offenders).as("files outside the Telegram folders that use org.telegram").isEmpty();
	}

	@Test
	void theScanActuallyFindsTheAdapterSources() throws IOException {
		try (Stream<Path> files = Files.walk(TRANSPORT)) {
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
