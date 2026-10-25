package com.massimotter.weave.backend.boards.domain;

import java.net.URI;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record TaskAttachment(
        String id,
        String taskId,
        AttachmentKind kind,
        String displayName,
        URI uri,
        Long size,
        String mimeType,
        List<ProviderRef> providerRefs) {

    public TaskAttachment {
        id = BoardsContract.requireText(id, "id");
        taskId = BoardsContract.requireText(taskId, "taskId");
        kind = requireNonNull(kind, "kind must not be null");
        displayName = BoardsContract.requireText(displayName, "displayName");
        uri = requireNonNull(uri, "uri must not be null");
        if (size != null && size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }
}
