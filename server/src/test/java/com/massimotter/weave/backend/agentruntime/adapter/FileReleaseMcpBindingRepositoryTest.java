package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.identity.IdentityReferences;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class FileReleaseMcpBindingRepositoryTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String SUBJECT = "service-account-release-member";

    @TempDir
    Path temporary;

    @Test
    void rereadsPrivateBindingAndObservesAtomicRevocation() throws Exception {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"));
        Path file = temporary.resolve("mcp-bindings.json");
        writePrivate(file, binding(true));
        var repository = new FileReleaseMcpBindingRepository(file, new ObjectMapper());

        var current = repository.findByWorkload(ISSUER, SUBJECT).orElseThrow();
        assertThat(current.workloadClientId()).isEqualTo("weaver-mcp-member-test");
        assertThat(current.allowedToolClasses()).containsExactly("calendar.read");
        assertThat(current.revision()).startsWith("sha256:");

        Path replacement = temporary.resolve("replacement.json");
        writePrivate(replacement, binding(false));
        Files.move(replacement, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThat(repository.findByWorkload(ISSUER, SUBJECT).orElseThrow().active()).isFalse();

        writePrivate(replacement, """
                {"schemaVersion":1,"bindings":[]}
                """);
        Files.move(replacement, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThat(repository.findByWorkload(ISSUER, SUBJECT)).isEmpty();
    }

    @Test
    void rejectsWorldReadableAndMalformedBindingState() throws Exception {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"));
        Path file = temporary.resolve("mcp-bindings.json");
        writePrivate(file, binding(true));
        var repository = new FileReleaseMcpBindingRepository(file, new ObjectMapper());

        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(() -> repository.findByWorkload(ISSUER, SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(file.toString());

        writePrivate(file, "{" + "\"schemaVersion\":1,\"bindings\":[{},{}]}");
        assertThatThrownBy(() -> repository.findByWorkload(ISSUER, SUBJECT))
                .isInstanceOf(IllegalStateException.class);

        writePrivate(file, binding(true).replace("\"active\": true", "\"active\": true, \"active\": false"));
        assertThatThrownBy(() -> repository.findByWorkload(ISSUER, SUBJECT))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void writePrivate(Path file, String value) throws Exception {
        Files.writeString(file, value);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    private static String binding(boolean active) {
        return """
                {
                  "schemaVersion": 1,
                  "bindings": [{
                    "bindingRef": "mcp-binding:test",
                    "workloadIssuer": "https://auth.weave.test/realms/weave",
                    "workloadSubject": "service-account-release-member",
                    "workloadClientId": "weaver-mcp-member-test",
                    "organizationRef": "org:test",
                    "personRef": "%s",
                    "memberIssuer": "https://auth.weave.test/realms/weave",
                    "memberSubject": "member-subject",
                    "expiresAt": "2026-12-31T00:00:00Z",
                    "allowedToolClasses": ["calendar.read"],
                    "active": %s
                  }]
                }
                """.formatted(IdentityReferences.accountId(ISSUER, "member-subject"), active);
    }
}
