package com.kayo.reminder.conventions.rule;

import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class RepositoryWithoutEntityManagerRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain).filter(file -> file.isUnder("domain.repositories"))
				.flatMap(file -> file.unit().getImports().stream()
						.filter(declaration -> declaration.getNameAsString().equals("jakarta.persistence.EntityManager"))
						.map(declaration -> at(file, declaration, "EntityManager in a repository")))
				.toList();
	}
}
