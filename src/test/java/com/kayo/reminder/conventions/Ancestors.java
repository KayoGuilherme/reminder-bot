package com.kayo.reminder.conventions;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import java.util.Optional;
import java.util.stream.Stream;

public final class Ancestors {

	private Ancestors() {
	}

	public static Stream<Node> withinBody(Node node) {
		return Stream.iterate(node.getParentNode(), Optional::isPresent, parent -> parent.get().getParentNode())
				.map(Optional::get)
				.takeWhile(parent -> !(parent instanceof CallableDeclaration) && !(parent instanceof LambdaExpr));
	}
}
