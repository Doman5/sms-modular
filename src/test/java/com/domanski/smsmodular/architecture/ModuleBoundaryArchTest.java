package com.domanski.smsmodular.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domanski.smsmodular.modulea.api.IllegalApiDependency;
import com.domanski.smsmodular.moduleb.domain.ForeignDomainType;
import com.domanski.smsmodular.moduleb.infrastructure.ForeignInfrastructureType;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.AnalyzeClasses;


@AnalyzeClasses(
	packages = "com.domanski.smsmodular",
	importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleBoundaryArchTest {

	@ArchTest
	static final ArchRule production_module_boundaries = classes()
		.should(notDependOnForeignModuleInternals());

	



	@Test
	void detectsIntentionalForeignDomainAndInfrastructureDependencies() {
		var fixture = new ClassFileImporter().importClasses(
			IllegalApiDependency.class,
			ForeignDomainType.class,
			ForeignInfrastructureType.class
		);

		assertThatThrownBy(() -> classes().should(notDependOnForeignModuleInternals()).check(fixture))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("foreign module internal");
	}

	private static ArchCondition<JavaClass> notDependOnForeignModuleInternals() {
		return new ArchCondition<>("not depend on foreign module internal packages") {
			@Override
			public void check(JavaClass source, ConditionEvents events) {
				String sourceModule = moduleRoot(source.getPackageName());
				if (sourceModule == null) {
					return;
				}
				for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
					JavaClass target = dependency.getTargetClass();
					String targetModule = moduleRoot(target.getPackageName());
					if (targetModule != null
						&& !sourceModule.equals(targetModule)
						&& isInternalLayer(target.getPackageName())) {
						events.add(SimpleConditionEvent.violated(
							source,
							"Class " + source.getName() + " has a foreign module internal dependency on "
								+ target.getName()
						));
					}
				}
			}
		};
	}

	private static String moduleRoot(String packageName) {
		String prefix = "com.domanski.smsmodular.";
		if (!packageName.startsWith(prefix)) {
			return null;
		}
		String remainder = packageName.substring(prefix.length());
		String[] segments = remainder.split("\\.");
		if (segments.length < 2 || switch (segments[0]) {
			case "common", "config", "api", "architecture", "support" -> true;
			default -> false;
		}) {
			return null;
		}
		return segments[0];
	}

	private static boolean isInternalLayer(String packageName) {
		return packageName.matches(".*\\.(domain|infrastructure)(\\..*)?");
	}
}
