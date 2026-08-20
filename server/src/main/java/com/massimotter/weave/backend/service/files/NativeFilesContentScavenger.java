package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Periodically reclaims bounded private Files content that has no relational protection. */
@Service
@ConditionalOnProperty(
        name = "weave.files.provider",
        havingValue = "weave-native",
        matchIfMissing = true)
public final class NativeFilesContentScavenger {

    static final Duration MINIMUM_AGE = Duration.ofHours(1);
    static final int BATCH_LIMIT = 64;

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeFilesContentScavenger.class);

    private final NativeFilesContentStore contentStore;

    public NativeFilesContentScavenger(@Lazy NativeFilesContentStore contentStore) {
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore must not be null");
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void scavengeScheduledBatch() {
        try {
            contentStore.scavengeBounded(Instant.now().minus(MINIMUM_AGE), BATCH_LIMIT);
        } catch (RuntimeException unavailableOrCorrupt) {
            // Scheduled work must remain retryable; support output deliberately excludes paths,
            // operation refs, bindings, capacity, owner markers, and raw exception messages.
            LOGGER.warn("Native Files private-content scavenging was deferred");
        }
    }
}
