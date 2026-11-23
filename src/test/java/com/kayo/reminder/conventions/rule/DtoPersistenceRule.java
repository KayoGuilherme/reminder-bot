package com.kayo.reminder.conventions.rule;

import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class DtoPersistenceRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain).filter(file -> file.isUnder("application.dto"))
				.flatMap(file -> file.unit().getImports().stream()
						.filter(declaration -> declaration.getNameAsString().startsWith("jakarta.persistence"))
						.map(declaration -> at(file, declaration, "persistence annotation in dto")))
				.toList();
	}
}
