package com.kayo.reminder.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeConventionsTest {

	@Test
	void sourceTreeFollowsTheEnabledConventions() {
		List<SourceFile> sources = SourceTree.load(Path.of("."));
		List<String> violations = ConventionRules.enabled().stream().flatMap(rule -> rule.check(sources).stream())
				.map(Violation::toString).toList();
		assertThat(violations).isEmpty();
	}
}
