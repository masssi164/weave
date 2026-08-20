package com.massimotter.weave.backend.files.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
        packages = "com.massimotter.weave.backend.files",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FilesCoreArchitectureTest {

    @Test
    void canonicalMetadataDoesNotExposePersistencePrivateBlobBindings() {
        var componentNames = Arrays.stream(CanonicalFileRecord.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertFalse(componentNames.contains("storageReference"));
        assertFalse(componentNames.contains("blobBinding"));
        assertFalse(componentNames.contains("blobReference"));
    }

    @ArchTest
    static final ArchRule CORE_HAS_NO_FRAMEWORK_TRANSPORT_OR_ADAPTER_DEPENDENCIES = noClasses()
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
                    "org.springframework.ai..",
                    "..adapter..",
                    "..controller..",
                    "..projection..",
                    "..persistence..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_APPLICATION_OR_PORTS = noClasses()
            .that()
            .resideInAPackage("..files.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..files.application..", "..files.port..");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_IMPLEMENTATIONS = noClasses()
            .that()
            .resideInAPackage("..files.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..adapter..",
                    "..controller..",
                    "..projection..",
                    "..persistence..");
}
