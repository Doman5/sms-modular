package com.domanski.smsmodular.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;


@AnalyzeClasses(
	packages = "com.domanski.smsmodular.audit",
	importOptions = ImportOption.DoNotIncludeTests.class
)
class AuditModuleBoundaryArchTest {

	@ArchTest
	static final ArchRule public_contract_does_not_depend_on_internals = noClasses()
		.that().resideInAnyPackage("com.domanski.smsmodular.audit.api.contract..")
		.should().dependOnClassesThat().resideInAnyPackage(
			"com.domanski.smsmodular.audit.infrastructure..",
			"com.domanski.smsmodular.audit.application..",
			"com.domanski.smsmodular.audit.domain.."
		);

	@ArchTest
	static final ArchRule controllers_do_not_depend_on_persistence = noClasses()
		.that().resideInAnyPackage("com.domanski.smsmodular.audit.api..")
		.should().dependOnClassesThat().resideInAnyPackage("com.domanski.smsmodular.audit.infrastructure..")
		.because("controllers use application services and never repositories or JPA entities");
}
