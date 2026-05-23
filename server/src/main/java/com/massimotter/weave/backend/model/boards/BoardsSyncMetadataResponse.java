package com.massimotter.weave.backend.model.boards;

import java.time.Instant;
import java.util.Map;

/**
 * Support-safe sync metadata for the hidden Boards preview facade. Cursors are
 * adapter-owned opaque values and must never contain provider URLs or secrets.
 */
public record BoardsSyncMetadataResponse(
        String provider,
        String mode,
        boolean readOnly,
        boolean contextScoped,
        boolean supportSafe,
        Map<String, String> nextCursors,
        Instant lastSyncedAt) {

    public BoardsSyncMetadataResponse {
        nextCursors = nextCursors == null ? Map.of() : Map.copyOf(nextCursors);
    }
}
