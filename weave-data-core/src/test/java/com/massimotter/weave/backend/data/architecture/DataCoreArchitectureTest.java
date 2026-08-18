package com.massimotter.weave.backend.data.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.massimotter.weave.backend.data",
        importOptions = ImportOption.DoNotIncludeTests.class)
class DataCoreArchitectureTest {

    @ArchTest
    static final ArchRule DATA_CORE_IS_PROVIDER_PERSISTENCE_AND_TRANSPORT_FREE = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "javax..",
                    "com.fasterxml..",
                    "tools.jackson..",
                    "org.apache.opendal..",
                    "net.fortuna.ical4j..",
                    "io.modelcontextprotocol..",
                    "org.springframework.ai..",
                    "..adapter..",
                    "..persistence..",
                    "..projection..",
                    "..controller..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_TRANSFER_OR_PORTS = noClasses()
            .that()
            .resideInAPackage("..data.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..data.transfer..", "..port..", "..adapter..");

    @ArchTest
    static final ArchRule DATA_CORE_SLICES_ARE_ACYCLIC = slices()
            .matching("com.massimotter.weave.backend.data.(*)..")
            .should()
            .beFreeOfCycles();
}
