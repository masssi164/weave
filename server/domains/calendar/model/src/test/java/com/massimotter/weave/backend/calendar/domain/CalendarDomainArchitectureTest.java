package com.massimotter.weave.backend.calendar.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.backend.calendar.domain",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CalendarDomainArchitectureTest {
    @ArchTest
    static final ArchRule canonicalCalendarUsesOnlyJavaAndItsOwnValues = classes()
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "com.massimotter.weave.backend.calendar.domain..");
}
