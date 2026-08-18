package com.massimotter.weave.core.transfer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.massimotter.weave.core.transfer",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CanonicalTransferModuleArchitectureTest {

    @ArchTest
    static final ArchRule KERNEL_HAS_NO_FRAMEWORK_PROTOCOL_PERSISTENCE_OR_PROVIDER_DEPENDENCY =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.servlet..",
                            "com.fasterxml.jackson..",
                            "tools.jackson..",
                            "org.apache.opendal..",
                            "net.fortuna.ical4j..",
                            "io.modelcontextprotocol..",
                            "software.amazon.awssdk..",
                            "org.matrix..",
                            "..adapter..",
                            "..controller..",
                            "..projection..",
                            "..persistence..");
}
