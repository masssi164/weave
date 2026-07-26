package com.massimotter.weave.backend.agentruntime.operator;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeProfileSigningKeyCliTest {
    @TempDir
    Path temporary;

    @Test
    void initializesAndInspectsOnlySupportSafeState() throws Exception {
        Invocation initialized = invoke(
                "--action=initialize",
                "--root=" + temporary,
                "--operation-ref=bootstrap:test-installation");

        assertThat(initialized.status()).isZero();
        assertThat(initialized.errors()).isEmpty();
        JsonNode state = new ObjectMapper().readTree(initialized.output());
        assertThat(state.path("activeKeyId").asString()).startsWith("rpk_");
        assertThat(state.path("keys").get(0).path("privateMaterialPresent").asBoolean()).isTrue();
        assertThat(initialized.output())
                .doesNotContain("privateKey")
                .doesNotContain("pk8")
                .doesNotContain(temporary.toString());

        Invocation status = invoke("--action=status", "--root=" + temporary);
        assertThat(status.status()).isZero();
        assertThat(new ObjectMapper().readTree(status.output()).path("activeKeyId").asString())
                .isEqualTo(state.path("activeKeyId").asString());
    }

    @Test
    void rejectsImplicitRootsUnknownArgumentsAndMissingOperationReferences() {
        assertThat(invoke("--action=status", "--root=relative/path").status()).isEqualTo(2);
        assertThat(invoke("--action=initialize", "--root=" + temporary).status()).isEqualTo(2);
        assertThat(invoke("--action=status", "--root=" + temporary, "--secret=value").status())
                .isEqualTo(2);
    }

    private Invocation invoke(String... arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status = RuntimeProfileSigningKeyCli.run(
                arguments,
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC),
                new SecureRandom());
        return new Invocation(
                status,
                output.toString(StandardCharsets.UTF_8).trim(),
                errors.toString(StandardCharsets.UTF_8).trim());
    }

    private record Invocation(int status, String output, String errors) {
    }
}
