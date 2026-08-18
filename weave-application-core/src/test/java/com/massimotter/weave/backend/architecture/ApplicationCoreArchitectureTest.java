package com.massimotter.weave.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.massimotter.weave.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationCoreArchitectureTest {

    @ArchTest
    static final ArchRule CORE_HAS_NO_FRAMEWORK_OR_ADAPTER_DEPENDENCIES = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax.persistence..",
                    "com.fasterxml..",
                    "tools.jackson..",
                    "org.apache.opendal..",
                    "net.fortuna.ical4j..",
                    "io.modelcontextprotocol..",
                    "org.springframework.ai..",
                    "..adapter..",
                    "..controller..",
                    "..projection..",
                    "..persistence..");

    @ArchTest
    static final ArchRule DOMAIN_DEPENDS_INWARD_ONLY = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..application..",
                    "..port..",
                    "..adapter..",
                    "..controller..",
                    "..projection..",
                    "..persistence..");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_IMPLEMENTATION_LAYERS = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..adapter..",
                    "..controller..",
                    "..projection..",
                    "..persistence..");

    @ArchTest
    static final ArchRule AGENT_RUNTIME_CORE_LAYERS_ARE_ACYCLIC = slices()
            .matching("com.massimotter.weave.backend.agentruntime.(*)..")
            .should()
            .beFreeOfCycles();
}
