package com.massimotter.weave.backend.data.adapter.persistence.jpa;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Checkpoint;
import static com.massimotter.weave.backend.data.domain.CanonicalData.CheckpointKey;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferRunId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferStage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JpaTransferCheckpointAdapterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T15:00:00Z"), ZoneOffset.UTC);
    private static final CheckpointKey KEY = new CheckpointKey(
            new TransferRunId("transfer-1"), TransferStage.EXPORT);

    private SpringDataTransferCheckpointRepository repository;
    private JpaTransferCheckpointAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(SpringDataTransferCheckpointRepository.class);
        adapter = new JpaTransferCheckpointAdapter(repository, CLOCK);
    }

    @Test
    void createsAndReadsCanonicalCheckpointMapping() {
        Checkpoint checkpoint = new Checkpoint(3, "cursor-3", false);
        when(repository.findById(any())).thenReturn(Optional.empty());

        adapter.save(KEY, checkpoint);

        ArgumentCaptor<JpaTransferCheckpointEntity> captor =
                ArgumentCaptor.forClass(JpaTransferCheckpointEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(KEY, captor.getValue().canonicalKey());
        assertEquals(checkpoint, captor.getValue().canonicalCheckpoint());
    }

    @Test
    void updatesCheckpointMonotonically() {
        JpaTransferCheckpointEntity existing = JpaTransferCheckpointEntity.from(
                KEY,
                new Checkpoint(3, "cursor-3", false),
                Instant.EPOCH);
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        Checkpoint next = new Checkpoint(5, null, true);
        adapter.save(KEY, next);

        assertEquals(next, existing.canonicalCheckpoint());
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void rejectsBackwardAndPostCompletionUpdates() {
        JpaTransferCheckpointEntity existing = JpaTransferCheckpointEntity.from(
                KEY,
                new Checkpoint(5, null, true),
                Instant.EPOCH);
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(KEY, new Checkpoint(4, null, true)));
        assertThrows(
                IllegalStateException.class,
                () -> adapter.save(KEY, new Checkpoint(6, null, true)));
    }

    @Test
    void returnsCanonicalCheckpointWithoutExposingJpaEntity() {
        Checkpoint checkpoint = new Checkpoint(2, null, true);
        when(repository.findById(any())).thenReturn(Optional.of(
                JpaTransferCheckpointEntity.from(KEY, checkpoint, Instant.EPOCH)));

        Optional<Checkpoint> result = adapter.find(KEY);

        assertTrue(result.isPresent());
        assertEquals(checkpoint, result.orElseThrow());
    }
}
