package com.kayo.reminder.conventions;

import com.github.javaparser.ast.Node;
import java.util.List;

public abstract class PerFileRule implements ConventionRule {

	protected abstract List<Violation> checkFile(SourceFile source);

	@Override
	public String name() {
		return getClass().getSimpleName();
	}

	@Override
	public List<Violation> check(List<SourceFile> sources) {
		return sources.stream().flatMap(source -> checkFile(source).stream()).toList();
	}

	protected Violation at(SourceFile source, Node node, String message) {
		return new Violation(name(), source.path(), node.getBegin().map(position -> position.line).orElse(1), message);
	}
}
