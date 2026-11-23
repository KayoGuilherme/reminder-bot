package com.kayo.reminder.conventions;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class SourceTree {

	private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");
	private static final JavaParser PARSER = new JavaParser(
			new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

	private SourceTree() {
	}

	public static List<SourceFile> load(Path projectRoot) {
		return SOURCE_ROOTS.stream().flatMap(root -> javaFiles(projectRoot, root)).map(file -> parseFile(projectRoot, file))
				.toList();
	}

	public static SourceFile parse(String path, String code) {
		return new SourceFile(path, PARSER.parse(code).getResult().orElseThrow());
	}

	private static SourceFile parseFile(Path projectRoot, Path file) {
		return parse(projectRoot.relativize(file).toString().replace('\\', '/'), read(file));
	}

	private static Stream<Path> javaFiles(Path projectRoot, String root) {
		try (Stream<Path> paths = Files.walk(projectRoot.resolve(root))) {
			return paths.filter(path -> path.toString().endsWith(".java")).toList().stream();
		}
		catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}
}
