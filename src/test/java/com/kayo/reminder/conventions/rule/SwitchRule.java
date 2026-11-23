package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class SwitchRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.<Node>concat(source.unit().findAll(SwitchStmt.class).stream(),
				source.unit().findAll(SwitchExpr.class).stream()).map(node -> at(source, node, "switch")).toList();
	}
}
