package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeCompositionArchitectureTest {

    @Test
    void bothApplicationsShareOnlyProviderNeutralRuntimeModules() throws IOException {
        Path root = repositoryRoot();
        for (String application : List.of("server", "weave-mcp-server")) {
            String build = Files.readString(root.resolve(application).resolve("build.gradle"));
            assertThat(build)
                    .contains("project(':weave-application-core')")
                    .contains("project(':weave-persistence-jpa')")
                    .contains("project(':weave-runtime-security-adapters')");
            if ("weave-mcp-server".equals(application)) {
                assertThat(build)
                        .doesNotContain("project(':server')")
                        .doesNotContain("project(':weave-runtime-provider-adapters')");
            } else {
                assertThat(build).contains("project(':weave-runtime-provider-adapters')");
            }
        }
    }

    @Test
    void sharedSecurityAdaptersContainNoServerOnlyIdentityAdministration() throws IOException {
        Path root = repositoryRoot();
        String sharedSecurity = allText(root.resolve("weave-runtime-security-adapters/src/main"));

        assertThat(sharedSecurity)
                .doesNotContain("KeycloakAdminAccessTokenProvider")
                .doesNotContain("KeycloakAgentRuntimeWorkloadIdentityAdmin")
                .doesNotContain("KeycloakRuntimeEntitlementAuthority")
                .doesNotContain("ClientSecretKeycloakAdminAccessTokenProvider")
                .doesNotContain("FileRuntimeWorkloadCredentialStore");
    }

    @Test
    void mcpBuildAppliesTheDedicatedVerificationScriptWithoutDefiningTasksInline() throws IOException {
        Path root = repositoryRoot();
        String build = Files.readString(root.resolve("weave-mcp-server/build.gradle"));
        String verification = Files.readString(
                root.resolve("weave-mcp-server/gradle/tasks/verification.gradle"));

        assertThat(build)
                .contains("apply from: \"$projectDir/gradle/tasks/verification.gradle\"")
                .doesNotContain("verifyLeastAuthorityBootJar")
                .doesNotContain("forbiddenMcpRuntimeClasses");
        assertThat(verification)
                .contains("tasks.register('verifyLeastAuthorityBootJar')")
                .contains("dependsOn tasks.named('bootJar')")
                .contains("dependsOn tasks.named('verifyLeastAuthorityBootJar')")
                .contains("weave-runtime-provider-adapters");
    }

    @Test
    void mcpHasNoAdminIdentityCredentialOrPrivateBridge() throws IOException {
        Path root = repositoryRoot();
        String mcp = allText(root.resolve("weave-mcp-server").resolve("src/main"));
        String server = allText(root.resolve("server").resolve("src/main"));

        assertThat(mcp)
                .doesNotContain("weave-agent-runtime-admin")
                .doesNotContain("weave-identity-admin")
                .doesNotContain("ADMIN_CREDENTIAL_REF")
                .doesNotContain("KeycloakAgentRuntimeWorkloadIdentityAdmin")
                .doesNotContain("ClientSecretKeycloakAdminAccessTokenProvider")
                .doesNotContain("/api/internal/agent-runtime/mcp-context")
                .doesNotContain("McpBackendContextResolver");
        assertThat(server)
                .doesNotContain("/api/internal/agent-runtime/mcp-context")
                .doesNotContain("McpWorkloadBridgeSecurityConfiguration");
    }

    @Test
    void mcpNeverOwnsMigrationsAndRequiresTheExactSharedSchema() throws IOException {
        Path root = repositoryRoot();
        List<Path> migrationRoots;
        try (var paths = Files.walk(root, 7)) {
            migrationRoots = paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.endsWith(Path.of("src/main/resources/db/migration")))
                    .toList();
        }

        assertThat(migrationRoots).containsExactly(
                root.resolve("weave-persistence-jpa/src/main/resources/db/migration"));
        assertThat(Files.readString(root.resolve("weave-mcp-server/src/main/resources/application.yml")))
                .containsSubsequence("flyway:", "enabled: false")
                .contains("weaveSchemaVersion");
        assertThat(Files.readString(root.resolve("server/src/main/resources/application.yml")))
                .containsSubsequence("flyway:", "enabled: true")
                .contains("weaveSchemaVersion");
        assertThat(Files.readString(root.resolve(
                "weave-persistence-jpa/src/main/java/com/massimotter/weave/shared/persistence/SharedPersistenceModel.java")))
                .contains("VERSION = \"019\"");
        assertThat(migrationRoots.getFirst().resolve("V019__identity_role_only_provisioning_intent.sql"))
                .isRegularFile();
    }

    @Test
    void coreIsTransportFrameworkPersistenceAndProviderNeutral() throws IOException {
        String core = allText(repositoryRoot().resolve("weave-application-core/src/main/java"));
        assertThat(core)
                .doesNotContain("import org.springframework.")
                .doesNotContain("import jakarta.persistence.")
                .doesNotContain("import io.modelcontextprotocol.")
                .doesNotContain("import com.massimotter.weave.backend.agentruntime.adapter.");
    }

    private static String allText(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                result.append(Files.readString(path)).append('\n');
            }
        }
        return result.toString();
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 4 && current != null; depth++, current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isDirectory(current.resolve("weave-mcp-server"))) {
                return current;
            }
        }
        throw new IllegalStateException("Unable to locate the Weave repository root");
    }
}
