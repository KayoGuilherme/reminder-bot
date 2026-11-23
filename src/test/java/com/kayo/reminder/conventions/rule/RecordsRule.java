package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.body.RecordDeclaration;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class RecordsRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(file -> !isAllowed(file)).flatMap(file -> file.unit().findAll(RecordDeclaration.class).stream()
				.map(declaration -> at(source, declaration, "record " + declaration.getNameAsString()))).toList();
	}

	private boolean isAllowed(SourceFile source) {
		return source.isMain() && (source.isUnder("application.dto") || source.isUnder("transport"));
	}
}
