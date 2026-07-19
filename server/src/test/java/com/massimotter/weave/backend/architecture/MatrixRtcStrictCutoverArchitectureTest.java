package com.massimotter.weave.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatrixRtcStrictCutoverArchitectureTest {

    @Test
    void proprietaryMemberCallsSurfaceCannotReturn() throws IOException {
        Path root = repositoryRoot();
        String mainSources = readTree(root.resolve("server/src/main/java"));
        String openApi = Files.readString(root.resolve("contracts/openapi/weave-openapi.json"));
        String dartModels = Files.readString(root.resolve("client/lib/generated/openapi_models.dart"));

        assertThat(mainSources)
                .doesNotContain("@GetMapping(\"/api/calls")
                .doesNotContain("@PostMapping(\"/api/calls")
                .doesNotContain("@RequestMapping(\"/api/calls")
                .doesNotContain("@GetMapping(\"/api/weave/calls")
                .doesNotContain("@PostMapping(\"/api/weave/calls")
                .doesNotContain("com.weave.call.")
                .doesNotContain("class CallsController")
                .doesNotContain("class CallsFacadeService");

        assertThat(openApi)
                .doesNotContain("\"/api/calls")
                .doesNotContain("CallCreateRequest")
                .doesNotContain("CallJoinResponse")
                .doesNotContain("CallNativeBoundarySetupResponse");

        assertThat(dartModels)
                .doesNotContain("class CallCreateRequest")
                .doesNotContain("class CallJoinResponse")
                .doesNotContain("class CallNativeBoundarySetupResponse");
    }

    @Test
    void profileZeroHasOneWireShapeAndNoCompatibilityReader() throws IOException {
        String profile = Files.readString(
                repositoryRoot().resolve("docs/architecture/matrixrtc-profile-0.yaml"));

        assertThat(profile)
                .contains("compatibility_policy: strict-cutover")
                .contains("read_policy: strict-profile-0-only")
                .contains("write_policy: strict-profile-0-only")
                .contains("reject_unknown_or_legacy_shapes: true")
                .doesNotContain("compatibility_reads:")
                .doesNotContain("dual_read_single_write: true")
                .doesNotContain("unstable_fallback_endpoint:");
    }

    private static String readTree(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            List<Path> sourceFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            StringBuilder result = new StringBuilder();
            for (Path sourceFile : sourceFiles) {
                result.append(Files.readString(sourceFile)).append('\n');
            }
            return result.toString();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("server/src/main/java"))) {
            return current;
        }
        if (Files.isDirectory(current.resolve("src/main/java"))
                && current.getParent() != null
                && Files.isDirectory(current.getParent().resolve("client"))) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to locate Weave repository root from " + current);
    }
}
