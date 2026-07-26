package com.massimotter.weave.backend.provider;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileProviderSelectionRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void providerSelectionSurvivesRepositoryRestart() {
        Path storagePath = tempDir.resolve("provider-selections.json");
        ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var repository = new FileProviderSelectionRepository(objectMapper, storagePath);

        repository.save(new ProviderSelection(
                "chat",
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                false,
                List.of("support-safe note")));

        var restartedRepository = new FileProviderSelectionRepository(objectMapper, storagePath);

        assertThat(restartedRepository.findByCategory("CHAT"))
                .isPresent()
                .get()
                .satisfies(selection -> {
                    assertThat(selection.category()).isEqualTo("chat");
                    assertThat(selection.providerKey()).isEqualTo("synapse-homeserver");
                    assertThat(selection.secretRef()).isEqualTo("secretref://weave/provider/synapse-homeserver");
                    assertThat(selection.applied()).isTrue();
                    assertThat(selection.lossyMappingNotes()).containsExactly("support-safe note");
                });
        assertThat(storagePath).exists();
        assertThat(repository.persistencePosture()).isEqualTo("durable-file-backed");
    }

    @Test
    void failedJsonWriteDoesNotExposeUnpersistedProviderSelection() throws IOException {
        Path blockedParent = Files.writeString(tempDir.resolve("not-a-directory"), "blocked");
        Path storagePath = blockedParent.resolve("provider-selections.json");
        ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var repository = new FileProviderSelectionRepository(objectMapper, storagePath);
        ProviderSelection selection = new ProviderSelection(
                "chat",
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                false,
                List.of());

        assertThatThrownBy(() -> repository.save(selection))
                .isInstanceOf(ProviderSelectionStoreException.class)
                .hasMessageContaining("Failed to persist provider selections");

        assertThat(repository.findByCategory("chat")).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }
}
