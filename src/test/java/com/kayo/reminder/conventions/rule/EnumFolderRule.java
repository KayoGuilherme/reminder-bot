package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.body.EnumDeclaration;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class EnumFolderRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain)
				.filter(file -> !file.relativePackage().equals("domain.models.enums"))
				.flatMap(file -> file.unit().findAll(EnumDeclaration.class).stream()
						.map(declaration -> at(file, declaration, "enum outside domain.models.enums")))
				.toList();
	}
}
