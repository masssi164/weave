package com.massimotter.weave.backend.security.workload;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.massimotter.weave.backend.security",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SharedWorkloadArchitectureTest {
    @ArchTest
    static final ArchRule sharedSecurityDoesNotDependOnOptionalDomainsOrApplicationComposition =
            noClasses().should().dependOnClassesThat().resideInAnyPackage(
                    "com.massimotter.weave.backend.agentruntime..",
                    "com.massimotter.weave.backend.files..",
                    "com.massimotter.weave.backend.calendar..",
                    "com.massimotter.weave.backend.chat..",
                    "com.massimotter.weave.backend.config..");
}
