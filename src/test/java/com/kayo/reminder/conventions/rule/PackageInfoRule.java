package com.kayo.reminder.conventions.rule;

import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class PackageInfoRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(file -> file.fileName().equals("package-info.java"))
				.map(file -> new Violation(name(), file.path(), 1, "package-info.java"))
				.toList();
	}
}
