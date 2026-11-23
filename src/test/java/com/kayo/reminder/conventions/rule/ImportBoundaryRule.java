package com.kayo.reminder.conventions.rule;

import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;
import java.util.stream.Stream;

public class ImportBoundaryRule extends PerFileRule {

	private final String name;
	private final String fromFolder;
	private final List<String> forbiddenFolders;

	public ImportBoundaryRule(String name, String fromFolder, List<String> forbiddenFolders) {
		this.name = name;
		this.fromFolder = fromFolder;
		this.forbiddenFolders = forbiddenFolders;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return Stream.of(source).filter(SourceFile::isMain).filter(file -> file.isUnder(fromFolder))
				.flatMap(file -> file.unit().getImports().stream()
						.filter(declaration -> isForbidden(declaration.getNameAsString()))
						.map(declaration -> at(file, declaration, "imports " + declaration.getNameAsString())))
				.toList();
	}

	private boolean isForbidden(String imported) {
		return forbiddenFolders.stream().anyMatch(folder -> imported.startsWith(SourceFile.BASE_PACKAGE + "." + folder + "."));
	}
}
