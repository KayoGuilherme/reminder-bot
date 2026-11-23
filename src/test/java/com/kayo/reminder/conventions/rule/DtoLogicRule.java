package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class DtoLogicRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain).filter(file -> file.isUnder("application.dto"))
				.flatMap(file -> file.unit().findAll(MethodDeclaration.class).stream()
						.filter(method -> !method.isStatic())
						.map(method -> at(file, method, "instance method in dto " + method.getNameAsString())))
				.toList();
	}
}
