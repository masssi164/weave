package com.massimotter.weave.backend.model.boards;

import java.time.Instant;
import java.util.Map;

/**
 * Support-safe sync metadata for the Boards workspace facade. Cursors are
 * adapter-owned opaque values and must never contain provider URLs or secrets.
 */
public record BoardsSyncMetadataResponse(
        String provider,
        String mode,
        boolean userWriteAudited,
        boolean contextScoped,
        boolean supportSafe,
        Map<String, String> nextCursors,
        Map<String, String> mappingRefs,
        String replacementPreviewState,
        Instant lastSyncedAt) {

    public BoardsSyncMetadataResponse {
        nextCursors = nextCursors == null ? Map.of() : Map.copyOf(nextCursors);
        mappingRefs = mappingRefs == null ? Map.of() : Map.copyOf(mappingRefs);
    }
}
