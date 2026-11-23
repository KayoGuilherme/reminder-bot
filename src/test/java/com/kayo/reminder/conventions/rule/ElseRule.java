package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.stmt.IfStmt;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;

public class ElseRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return source.unit().findAll(IfStmt.class).stream().filter(IfStmt::hasElseBranch)
				.map(statement -> at(source, statement.getElseStmt().orElseThrow(), "else")).toList();
	}
}
