package com.massimotter.weave.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
        packages = "com.massimotter.weave.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CanonicalTransferKernelArchitectureTest {

    @ArchTest
    static final ArchRule TRANSFER_KERNEL_IS_FRAMEWORK_AND_ADAPTER_FREE = noClasses()
            .that()
            .resideInAnyPackage(
                    "..datasovereignty..",
                    "..transfer..",
                    "..portability..")
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
                    "..adapter..",
                    "..controller..",
                    "..projection..",
                    "..persistence..");

    @Test
    void canonicalTransferFoundationDeclaresRequiredDataSovereigntyConcepts() throws IOException {
        List<Path> sources = transferSources();
        assertFalse(sources.isEmpty(), "canonical transfer kernel sources are required");

        String source = readCombined(sources);
        for (String fidelityClass : List.of(
                "PORTABLE",
                "LOSSY",
                "UNSUPPORTED",
                "MANUAL_REVIEW",
                "VENDOR_LOCKED",
                "ARCHIVE_ONLY")) {
            assertTrue(source.contains(fidelityClass),
                    () -> "missing transfer fidelity class " + fidelityClass);
        }

        assertTrue(containsAny(source, "TransferCheckpoint", "Checkpoint"),
                "a resumable transfer checkpoint contract is required");
        assertTrue(containsAny(source, "ProviderSourceConnector", "SourceConnector"),
                "a provider source connector contract is required");
        assertTrue(containsAny(source, "ProviderTargetConnector", "TargetConnector"),
                "a provider target connector contract is required");
        assertTrue(containsAny(source, "transferFormatVersion", "TransferFormatVersion"),
                "the transfer format needs an independent version coordinate");
        assertTrue(containsAny(source, "canonicalModelVersion", "CanonicalModelVersion"),
                "the canonical model needs an independent version coordinate");
    }

    private static List<Path> transferSources() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(CanonicalTransferKernelArchitectureTest::isTransferSource)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isTransferSource(Path path) {
        String normalized = path.toString()
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        return normalized.contains("transfer")
                || normalized.contains("portability")
                || normalized.contains("datasovereignty");
    }

    private static String readCombined(List<Path> sources) throws IOException {
        StringBuilder combined = new StringBuilder();
        for (Path source : sources) {
            combined.append(Files.readString(source)).append('\n');
        }
        return combined.toString();
    }

    private static boolean containsAny(String source, String first, String second) {
        return source.contains(first) || source.contains(second);
    }
}
