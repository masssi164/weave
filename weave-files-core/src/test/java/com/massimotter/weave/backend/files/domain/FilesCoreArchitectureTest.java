package com.massimotter.weave.backend.files.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.massimotter.weave.backend.files.domain",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FilesCoreArchitectureTest {

    @ArchTest
    static final ArchRule CORE_HAS_NO_FRAMEWORK_OR_TRANSPORT_DEPENDENCIES = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "com.fasterxml.jackson..",
                    "tools.jackson..",
                    "io.modelcontextprotocol..",
                    "org.springframework.ai..");
}
