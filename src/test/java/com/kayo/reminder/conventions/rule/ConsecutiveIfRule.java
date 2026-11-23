package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ConsecutiveIfRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return source.unit().findAll(BlockStmt.class).stream()
				.flatMap(block -> consecutive(source, block.getStatements())).toList();
	}

	private Stream<Violation> consecutive(SourceFile source, List<Statement> statements) {
		return IntStream.range(1, statements.size())
				.filter(index -> statements.get(index) instanceof IfStmt && statements.get(index - 1) instanceof IfStmt)
				.mapToObj(index -> at(source, statements.get(index), "consecutive if"));
	}
}
