package com.massimotter.weave.backend.boards.domain;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record BoardColumn(
        String id,
        String boardId,
        String name,
        int position,
        ColumnSemanticStatus semanticStatus,
        Integer wipLimit,
        List<ProviderRef> providerRefs) {

    public BoardColumn {
        id = BoardsContract.requireText(id, "id");
        boardId = BoardsContract.requireText(boardId, "boardId");
        name = BoardsContract.requireText(name, "name");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        semanticStatus = requireNonNull(semanticStatus, "semanticStatus must not be null");
        if (wipLimit != null && wipLimit < 1) {
            throw new IllegalArgumentException("wipLimit must be positive when present");
        }
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }
}
