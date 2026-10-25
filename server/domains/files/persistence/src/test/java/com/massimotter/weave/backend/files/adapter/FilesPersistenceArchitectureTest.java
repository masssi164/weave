package com.massimotter.weave.backend.files.adapter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/** Persistence implements Files ports; it cannot reach Server facades or another product domain. */
@AnalyzeClasses(
        packages = "com.massimotter.weave.backend.files.adapter",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FilesPersistenceArchitectureTest {
    @ArchTest
    static final ArchRule noDeliveryOrForeignDomainDependencies = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.massimotter.weave.backend.controller..",
                    "com.massimotter.weave.backend.service..",
                    "com.massimotter.weave.backend.config..",
                    "com.massimotter.weave.backend.provider..",
                    "com.massimotter.weave.backend.calendar..",
                    "com.massimotter.weave.backend.chat..",
                    "com.massimotter.weave.backend.agentruntime..",
                    "com.massimotter.weave.mcp..");

    @ArchTest
    static final ArchRule entitiesDoNotDependOnFrameworkServices = noClasses()
            .that().areAnnotatedWith(Entity.class)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.hibernate..",
                    "com.fasterxml..", "tools.jackson..");
}
