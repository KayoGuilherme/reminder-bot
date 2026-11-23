package com.kayo.reminder.conventions;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConventionRulesTest {

	private static final String MAIN = "src/main/java/com/kayo/reminder/";
	private static final String TEST = "src/test/java/com/kayo/reminder/";

	private static SourceFile fixture(String path, String name) {
		try {
			return SourceTree.parse(path, Files.readString(Path.of("src/test/resources/conventions", name + ".txt")));
		}
		catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	private static List<Violation> check(ConventionRule rule, SourceFile... sources) {
		return rule.check(List.of(sources));
	}

	@Test
	void commentsAreRejected() {
		List<Violation> violations = check(new CommentsRule(), fixture(MAIN + "service/Sample.java", "comments-violation"));

		assertThat(violations).extracting(Violation::line).containsExactlyInAnyOrder(3, 5, 6);
	}

	@Test
	void commentMarkersInsideTextAreAccepted() {
		assertThat(check(new CommentsRule(), fixture(MAIN + "service/Sample.java", "comments-clean"))).isEmpty();
	}

	@Test
	void packageInfoIsRejected() {
		assertThat(check(new PackageInfoRule(), fixture(MAIN + "service/package-info.java", "package-info"))).hasSize(1);
	}

	@Test
	void recordsAreRejected() {
		assertThat(check(new RecordsRule(), fixture(MAIN + "domain/models/Sample.java", "records-violation"))).hasSize(2);
	}

	@Test
	void recordInsideTheDtoFolderIsAccepted() {
		assertThat(check(new RecordsRule(), fixture(MAIN + "application/dto/response/Sample.java", "records-dto")))
				.isEmpty();
	}

	@Test
	void enumOutsideTheEnumsFolderIsRejected() {
		assertThat(check(new EnumFolderRule(), fixture(MAIN + "application/dto/response/Sample.java", "enum-outside"))).hasSize(1);
	}

	@Test
	void enumInsideTheEnumsFolderIsAccepted() {
		assertThat(check(new EnumFolderRule(), fixture(MAIN + "domain/models/enums/Sample.java", "enum"))).isEmpty();
	}

	@Test
	void elseAndElseIfAreRejected() {
		assertThat(check(new ElseRule(), fixture(MAIN + "service/Sample.java", "else-violation"))).hasSize(2);
	}

	@Test
	void switchStatementsAndExpressionsAreRejected() {
		assertThat(check(new SwitchRule(), fixture(MAIN + "service/Sample.java", "switch-violation"))).hasSize(2);
	}

	@Test
	void nestedIfIsRejected() {
		assertThat(check(new NestedIfRule(), fixture(MAIN + "service/Sample.java", "nested-if-violation"))).hasSize(1);
	}

	@Test
	void ifInsideALambdaOfAnIfIsAccepted() {
		assertThat(check(new NestedIfRule(), fixture(MAIN + "service/Sample.java", "nested-if-clean"))).isEmpty();
	}

	@Test
	void consecutiveIfsAreRejected() {
		assertThat(check(new ConsecutiveIfRule(), fixture(MAIN + "service/Sample.java", "consecutive-if-violation")))
				.hasSize(1);
	}

	@Test
	void returnsOutsideTailPositionAreRejected() {
		assertThat(check(new TailReturnRule(), fixture(MAIN + "service/Sample.java", "return-violation"))).hasSize(2);
	}

	@Test
	void returnsAtTheEndOfTryCatchAndLambdasAreAccepted() {
		assertThat(check(new TailReturnRule(), fixture(MAIN + "service/Sample.java", "return-clean"))).isEmpty();
	}

	@Test
	void serviceInsideComponentFolderIsRejected() {
		assertThat(check(new FolderRule(), fixture(MAIN + "application/component/Sample.java", "service-in-component"))).hasSize(1);
	}

	@Test
	void classOutsideTheSemanticFoldersIsRejected() {
		assertThat(check(new FolderRule(), fixture(MAIN + "contract/Sample.java", "outside-folders"))).hasSize(1);
	}

	@Test
	void classInsideASubfolderOfARoleIsRejected() {
		assertThat(check(new FolderRule(), fixture(MAIN + "application/service/task/Sample.java", "subfolder"))).hasSize(1);
	}

	@Test
	void entityOutsideTheDomainFolderIsRejected() {
		assertThat(check(new FolderRule(), fixture(MAIN + "application/component/TaskEntity.java", "entity"))).hasSize(1);
	}

	@Test
	void testOutsideTheFolderOfItsTargetIsRejected() {
		SourceFile target = fixture(MAIN + "application/service/TaskService.java", "target");

		assertThat(check(new TestMirrorRule(), target, fixture(TEST + "application/component/TaskServiceTest.java", "target-test")))
				.hasSize(1);
	}

	@Test
	void testInTheFolderOfItsTargetIsAccepted() {
		SourceFile target = fixture(MAIN + "application/service/TaskService.java", "target");

		assertThat(check(new TestMirrorRule(), target,
				fixture(TEST + "application/component/TaskServiceTest.java", "target-test-mirrored"))).isEmpty();
	}

	@Test
	void domainImportingTheApplicationIsRejected() {
		ConventionRule rule = new ImportBoundaryRule("DomainLayerRule", "domain", List.of("application", "transport", "config"));

		assertThat(check(rule, fixture(MAIN + "domain/repositories/Sample.java", "repository-imports-service"))).hasSize(1);
	}

	@Test
	void instanceMethodInADtoIsRejected() {
		assertThat(check(new DtoLogicRule(), fixture(MAIN + "application/dto/response/Task.java", "model-logic"))).hasSize(1);
	}

	@Test
	void staticFactoryInADtoIsAccepted() {
		assertThat(check(new DtoLogicRule(), fixture(MAIN + "application/dto/response/Task.java", "model-factory"))).isEmpty();
	}

	@Test
	void exceptionOutsideTheDomainIsRejected() {
		assertThat(check(new ExceptionFolderRule(), fixture(MAIN + "application/dto/response/TaskException.java", "exception"))).hasSize(1);
	}

	@Test
	void exceptionInsideTheDomainIsAccepted() {
		assertThat(check(new ExceptionFolderRule(), fixture(MAIN + "domain/exceptions/TaskException.java", "exception-domain")))
				.isEmpty();
	}

	@Test
	void entityManagerInARepositoryIsRejected() {
		assertThat(check(new RepositoryWithoutEntityManagerRule(),
				fixture(MAIN + "domain/repositories/TaskRepository.java", "repository-entity-manager"))).hasSize(1);
	}

	@Test
	void persistenceAnnotationInADtoIsRejected() {
		assertThat(check(new DtoPersistenceRule(), fixture(MAIN + "application/dto/response/Task.java", "model-persistence")))
				.hasSize(1);
	}

	@Test
	void everyRuleHasADistinctName() {
		assertThat(ConventionRules.all().stream().map(ConventionRule::name).distinct().count())
				.isEqualTo(ConventionRules.all().size());
	}
}
