package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileProviderSelectionRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void providerSelectionSurvivesRepositoryRestart() {
        Path storagePath = tempDir.resolve("provider-selections.json");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
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
    void fileAndMemoryRepositoriesNormalizeCategoryKeysEquivalently() {
        Path storagePath = tempDir.resolve("provider-selections.json");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var fileRepository = new FileProviderSelectionRepository(objectMapper, storagePath);
        var memoryRepository = new InMemoryProviderSelectionRepository();
        ProviderSelection selection = new ProviderSelection(
                " CHAT ",
                "slack",
                "hybrid_composite",
                "secretref://weave/provider/slack",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                true,
                List.of("support-safe note"));

        fileRepository.save(selection);
        memoryRepository.save(selection);

        assertThat(fileRepository.findByCategory("chat")).isPresent();
        assertThat(memoryRepository.findByCategory("chat")).isPresent();
        assertThat(fileRepository.findByCategory(" CHAT ")).map(ProviderSelection::category).contains("chat");
        assertThat(memoryRepository.findByCategory(" CHAT ")).map(ProviderSelection::category).contains("chat");
        assertThat(fileRepository.findAll()).extracting(ProviderSelection::category).containsExactly("chat");
        assertThat(memoryRepository.findAll()).extracting(ProviderSelection::category).containsExactly("chat");
    }

    @Test
    void failedJsonWriteDoesNotExposeUnpersistedProviderSelection() throws IOException {
        Path storagePath = tempDir.resolve("provider-selections.json");
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ObjectWriter writer = mock(ObjectWriter.class);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
        doThrow(new IOException("write failed")).when(writer).writeValue(any(File.class), any());
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
