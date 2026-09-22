package com.domanski.smsmodular.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayeredArchitectureTest {

	@Test
	void servicesUseMethodLevelTransactions() {
		classes().that().resideInAPackage("..service..")
				.should().notBeAnnotatedWith(Transactional.class)
				.check(new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
						.importPackages("com.domanski.smsmodular"));
	}

	@Test
	void modulesDoNotImportForeignEntitiesOrRepositories() {
		var imported = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("com.domanski.smsmodular");
		noClasses().that().resideInAPackage("..identity..")
				.should().dependOnClassesThat().resideInAnyPackage("..tenancy.entity..", "..tenancy.repository..")
				.check(imported);
		noClasses().that().resideInAPackage("..tenancy..")
				.should().dependOnClassesThat().resideInAnyPackage("..identity.entity..", "..identity.repository..")
				.check(imported);
	}
}
