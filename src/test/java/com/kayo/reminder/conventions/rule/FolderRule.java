package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FolderRule extends PerFileRule {

	private static final Map<String, String> ROLE_BY_ANNOTATION = Map.of("Service", "application.service",
			"Component", "application.component", "Configuration", "config", "Entity", "domain.models");

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain).flatMap(this::violations).toList();
	}

	private Stream<Violation> violations(SourceFile source) {
		return Stream.concat(outsidePattern(source), annotationMismatch(source));
	}

	private Stream<Violation> outsidePattern(SourceFile source) {
		return Stream.of(source).filter(file -> !isEntryPoint(file))
				.filter(file -> !SourceFile.PACKAGES.contains(file.relativePackage()))
				.map(file -> new Violation(name(), file.path(), 1, "not inside one of the allowed folders"));
	}

	private boolean isEntryPoint(SourceFile source) {
		return source.packageName().equals(SourceFile.BASE_PACKAGE) && source.fileName().equals("BotApplication.java");
	}

	private Stream<Violation> annotationMismatch(SourceFile source) {
		return source.unit().findAll(TypeDeclaration.class).stream()
				.flatMap(type -> ROLE_BY_ANNOTATION.entrySet().stream()
						.filter(entry -> type.isAnnotationPresent(entry.getKey()))
						.filter(entry -> !source.relativePackage().equals(entry.getValue()))
						.map(entry -> at(source, type, "@" + entry.getKey() + " outside " + entry.getValue())));
	}
}
