package com.massimotter.weave.backend.boards.vikunja;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public record VikunjaTaskSnapshot(
        long id,
        long projectId,
        long bucketId,
        String title,
        String description,
        boolean done,
        int position,
        List<String> assigneeRefs,
        List<String> labelRefs,
        Integer priority,
        Instant startAt,
        Instant dueAt,
        Instant doneAt,
        Instant updatedAt,
        String etag,
        URI webUrl) {

    public VikunjaTaskSnapshot {
        assigneeRefs = List.copyOf(assigneeRefs == null ? List.of() : assigneeRefs);
        labelRefs = List.copyOf(labelRefs == null ? List.of() : labelRefs);
    }
}
