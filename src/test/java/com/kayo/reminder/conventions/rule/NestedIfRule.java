package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.stmt.IfStmt;
import com.kayo.reminder.conventions.Ancestors;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;

public class NestedIfRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return source.unit().findAll(IfStmt.class).stream()
				.filter(statement -> Ancestors.withinBody(statement).anyMatch(IfStmt.class::isInstance))
				.map(statement -> at(source, statement, "nested if")).toList();
	}
}
