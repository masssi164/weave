package com.massimotter.weave.backend.service.files;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NativeFilesContentScavengerTest {

    @Test
    void scheduledPassIsBoundedAndRemainsRetryableAfterAnUnavailablePass() {
        NativeFilesContentStore store = mock(NativeFilesContentStore.class);
        NativeFilesContentScavenger scavenger = new NativeFilesContentScavenger(store);
        doThrow(new IllegalStateException("private detail"))
                .doNothing()
                .when(store)
                .scavengeBounded(any(Instant.class), eq(NativeFilesContentScavenger.BATCH_LIMIT));

        scavenger.scavengeScheduledBatch();
        scavenger.scavengeScheduledBatch();

        verify(store, times(2))
                .scavengeBounded(any(Instant.class), eq(NativeFilesContentScavenger.BATCH_LIMIT));
    }
}
