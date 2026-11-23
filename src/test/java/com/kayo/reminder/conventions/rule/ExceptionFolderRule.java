package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class ExceptionFolderRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain).filter(file -> !file.relativePackage().equals("domain.exceptions"))
				.flatMap(file -> file.unit().findAll(TypeDeclaration.class).stream()
						.filter(type -> type.getNameAsString().endsWith("Exception"))
						.map(type -> at(file, type, "exception outside domain.exceptions")))
				.toList();
	}
}
