package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.kayo.reminder.conventions.ConventionRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestMirrorRule implements ConventionRule {

	@Override
	public String name() {
		return getClass().getSimpleName();
	}

	@Override
	public List<Violation> check(List<SourceFile> sources) {
		Map<String, String> packageByMainType = sources.stream().filter(SourceFile::isMain)
				.flatMap(source -> source.unit().findAll(TypeDeclaration.class).stream()
						.map(type -> Map.entry(type.getNameAsString(), source.packageName())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
		return sources.stream().filter(source -> !source.isMain())
				.filter(source -> isMisplaced(source, packageByMainType))
				.map(source -> new Violation(name(), source.path(), 1, "test is not in the folder of its target"))
				.toList();
	}

	private boolean isMisplaced(SourceFile source, Map<String, String> packageByMainType) {
		String testName = source.fileName().replace(".java", "");
		String target = testName.endsWith("Test") ? testName.substring(0, testName.length() - 4) : "";
		return packageByMainType.containsKey(target) && !packageByMainType.get(target).equals(source.packageName());
	}
}
