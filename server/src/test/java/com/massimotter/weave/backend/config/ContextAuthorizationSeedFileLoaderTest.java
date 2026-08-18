package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.context.authz.ContextMembership;
import com.massimotter.weave.backend.context.authz.ContextRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextAuthorizationSeedFileLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsExactlyThreePrivateMemberships() throws IOException {
        Path seed = privateSeed(validSeed());

        List<ContextMembership> memberships = ContextAuthorizationSeedFileLoader.load(
                new ContextAuthorizationSeedFileProperties(seed.toString()),
                List.of());

        assertThat(memberships)
                .hasSize(3)
                .extracting(ContextMembership::contextId)
                .containsExactly("workspace-default", "workspace-default", "outside-proof");
        assertThat(memberships)
                .extracting(ContextMembership::role)
                .containsExactly(ContextRole.OWNER, ContextRole.MEMBER, ContextRole.MEMBER);
    }

    @Test
    void rejectsInlineAndFileSourcesTogether() throws IOException {
        Path seed = privateSeed(validSeed());
        ContextMembership inline = new ContextMembership(
                "tenant-default", "workspace-default", "user:inline", ContextRole.MEMBER, "test");

        assertThatThrownBy(() -> ContextAuthorizationSeedFileLoader.load(
                new ContextAuthorizationSeedFileProperties(seed.toString()),
                List.of(inline)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(seed.toString());
    }

    @Test
    void rejectsWrongModeUnknownFieldsAndWrongCardinality() throws IOException {
        Path wrongMode = privateSeed(validSeed());
        Files.setPosixFilePermissions(wrongMode, PosixFilePermissions.fromString("rw-r-----"));
        assertInvalid(wrongMode);

        Path unknown = privateSeed(validSeed().replace(
                "\"source\":\"isolated-testapp-invitation\"",
                "\"source\":\"isolated-testapp-invitation\",\"unexpected\":true"));
        assertInvalid(unknown);

        Path wrongCount = privateSeed(twoRecordSeed());
        assertInvalid(wrongCount);
    }

    @Test
    void rejectsSymlinkAndDuplicateMembership() throws IOException {
        Path target = privateSeed(validSeed());
        Path link = temporaryDirectory.resolve("seed-link.json");
        Files.createSymbolicLink(link, target);
        assertInvalid(link);

        Path duplicate = privateSeed(validSeed().replace(
                "\"contextId\":\"outside-proof\",\"principalRef\":\"user:outsider\"",
                "\"contextId\":\"workspace-default\",\"principalRef\":\"user:member\""));
        assertInvalid(duplicate);
    }

    private void assertInvalid(Path path) {
        assertThatThrownBy(() -> ContextAuthorizationSeedFileLoader.load(
                new ContextAuthorizationSeedFileProperties(path.toString()),
                List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The private Context authorization seed is invalid.");
    }

    private Path privateSeed(String body) throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "context-seed-", ".json");
        Files.writeString(path, body);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private static String validSeed() {
        return """
                {"schemaVersion":"weave.context-authorization-seed/v1","memberships":[
                  {"tenantId":"tenant-default","contextId":"workspace-default","principalRef":"user:owner","role":"OWNER","source":"isolated-testapp-invitation"},
                  {"tenantId":"tenant-default","contextId":"workspace-default","principalRef":"user:member","role":"MEMBER","source":"isolated-testapp-invitation"},
                  {"tenantId":"tenant-default","contextId":"outside-proof","principalRef":"user:outsider","role":"MEMBER","source":"isolated-testapp-invitation"}
                ]}
                """;
    }

    private static String twoRecordSeed() {
        return """
                {"schemaVersion":"weave.context-authorization-seed/v1","memberships":[
                  {"tenantId":"tenant-default","contextId":"workspace-default","principalRef":"user:owner","role":"OWNER","source":"isolated-testapp-invitation"},
                  {"tenantId":"tenant-default","contextId":"workspace-default","principalRef":"user:member","role":"MEMBER","source":"isolated-testapp-invitation"}
                ]}
                """;
    }
}
