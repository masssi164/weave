package com.massimotter.weave.backend.calendar.persistence;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.massimotter.weave.backend.calendar.persistence",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CalendarPersistenceArchitectureTest {
    @ArchTest
    static final ArchRule noDeliveryProviderOrForeignDomainDependencies = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.massimotter.weave.backend.controller..",
                    "com.massimotter.weave.backend.service..",
                    "com.massimotter.weave.backend.config..",
                    "com.massimotter.weave.backend.files..",
                    "com.massimotter.weave.backend.chat..",
                    "com.massimotter.weave.backend.agentruntime..",
                    "com.massimotter.weave.mcp..", "net.fortuna.ical4j..");

    @ArchTest
    static final ArchRule entitiesRemainPortableMappings = noClasses()
            .that().areAnnotatedWith(Entity.class)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "org.hibernate..", "com.fasterxml..", "tools.jackson..");
}
