package com.kayo.reminder.conventions;

import java.util.List;

public interface ConventionRule {

	String name();

	List<Violation> check(List<SourceFile> sources);
}
