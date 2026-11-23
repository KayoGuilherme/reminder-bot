package com.kayo.reminder.conventions.rule;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;

public class TailReturnRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return source.unit().findAll(ReturnStmt.class).stream().filter(statement -> !isTail(statement))
				.map(statement -> at(source, statement, "return outside tail position")).toList();
	}

	private boolean isTail(Statement statement) {
		return statement.getParentNode().filter(BlockStmt.class::isInstance).map(BlockStmt.class::cast)
				.filter(block -> isLast(block, statement)).map(this::blockIsTail).orElse(false);
	}

	private boolean isLast(BlockStmt block, Statement statement) {
		return block.getStatements().getLast().filter(last -> last == statement).isPresent();
	}

	private boolean blockIsTail(BlockStmt block) {
		Node owner = block.getParentNode().orElseThrow();
		return owner instanceof CallableDeclaration<?> || owner instanceof LambdaExpr || isTailTryBlock(owner, block)
				|| isTailCatch(owner);
	}

	private boolean isTailTryBlock(Node owner, BlockStmt block) {
		return owner instanceof TryStmt tryStatement && tryStatement.getTryBlock() == block && isTail(tryStatement);
	}

	private boolean isTailCatch(Node owner) {
		return owner instanceof CatchClause clause && clause.getParentNode().map(TryStmt.class::cast).filter(this::isTail)
				.isPresent();
	}
}
