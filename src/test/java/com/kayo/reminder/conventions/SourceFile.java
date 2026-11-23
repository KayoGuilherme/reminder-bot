package com.kayo.reminder.conventions;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.List;
import lombok.Value;

@Value
public class SourceFile {

	public static final String BASE_PACKAGE = "com.kayo.reminder";
	public static final List<String> PACKAGES = List.of("domain.models", "domain.models.enums", "domain.exceptions",
			"domain.repositories", "application.dto.request", "application.dto.response", "application.service",
			"application.component", "transport.telegram", "transport.stt", "transport.llm", "transport.http",
			"config");

	String path;
	CompilationUnit unit;

	public String fileName() {
		return Path.of(path).getFileName().toString();
	}

	public boolean isMain() {
		return path.startsWith("src/main/java/");
	}

	public String packageName() {
		return unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
	}

	public String relativePackage() {
		return packageName().startsWith(BASE_PACKAGE + ".") ? packageName().substring(BASE_PACKAGE.length() + 1) : "";
	}

	public String firstFolder() {
		return relativePackage().split("\\.")[0];
	}

	public boolean isUnder(String folder) {
		return relativePackage().equals(folder) || relativePackage().startsWith(folder + ".");
	}
}
