package com.massimotter.weave.backend.chat;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = {"com.massimotter.weave.backend.chat", "com.massimotter.weave.backend.matrix"})
class ChatOwnershipTest {
    @ArchTest
    static final ArchRule actualRuntimeDoesNotImportAppOrFutureImplementations = noClasses().should()
            .dependOnClassesThat().resideInAnyPackage(
                    "com.massimotter.weave.backend.service..", "com.massimotter.weave.backend.controller..",
                    "com.massimotter.weave.backend.config..", "com.massimotter.weave.backend.model..",
                    "com.massimotter.weave.backend.provider..", "com.massimotter.weave.backend.decisions..",
                    "com.massimotter.weave.backend.meetings..");
}
