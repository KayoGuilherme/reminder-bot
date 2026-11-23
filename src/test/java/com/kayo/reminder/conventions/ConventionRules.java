package com.kayo.reminder.conventions;

import com.kayo.reminder.conventions.rule.CommentsRule;
import com.kayo.reminder.conventions.rule.ConsecutiveIfRule;
import com.kayo.reminder.conventions.rule.ElseRule;
import com.kayo.reminder.conventions.rule.EnumFolderRule;
import com.kayo.reminder.conventions.rule.ExceptionFolderRule;
import com.kayo.reminder.conventions.rule.FolderRule;
import com.kayo.reminder.conventions.rule.ImportBoundaryRule;
import com.kayo.reminder.conventions.rule.DtoLogicRule;
import com.kayo.reminder.conventions.rule.DtoPersistenceRule;
import com.kayo.reminder.conventions.rule.NestedIfRule;
import com.kayo.reminder.conventions.rule.PackageInfoRule;
import com.kayo.reminder.conventions.rule.RecordsRule;
import com.kayo.reminder.conventions.rule.RepositoryWithoutEntityManagerRule;
import com.kayo.reminder.conventions.rule.SwitchRule;
import com.kayo.reminder.conventions.rule.TailReturnRule;
import com.kayo.reminder.conventions.rule.TestMirrorRule;
import java.util.List;
import java.util.Set;

public final class ConventionRules {

	private static final Set<String> ENABLED = Set.of(
			"PackageInfoRule",
			"CommentsRule",
			"RecordsRule",
			"ElseRule",
			"SwitchRule",
			"NestedIfRule",
			"ConsecutiveIfRule",
			"TailReturnRule",
			"FolderRule",
			"TestMirrorRule",
			"DomainLayerRule",
			"DtoPersistenceRule",
			"DtoLogicRule",
			"ExceptionFolderRule",
			"EnumFolderRule",
			"RepositoryWithoutEntityManagerRule");

	private ConventionRules() {
	}

	public static List<ConventionRule> all() {
		return List.of(new CommentsRule(), new PackageInfoRule(), new RecordsRule(), new ElseRule(), new SwitchRule(),
				new NestedIfRule(), new ConsecutiveIfRule(), new TailReturnRule(), new FolderRule(), new TestMirrorRule(),
				new ImportBoundaryRule("DomainLayerRule", "domain", List.of("application", "transport", "config")),
				new DtoPersistenceRule(), new DtoLogicRule(), new ExceptionFolderRule(), new EnumFolderRule(),
				new RepositoryWithoutEntityManagerRule());
	}

	public static List<ConventionRule> enabled() {
		return Boolean.getBoolean("conventions.all") ? all()
				: all().stream().filter(rule -> ENABLED.contains(rule.name())).toList();
	}
}
