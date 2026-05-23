package com.massimotter.weave.backend.boards.domain;

import java.util.List;

public record Board(
        String id,
        String projectId,
        String name,
        String description,
        List<BoardColumn> columns,
        boolean archived,
        List<ProviderRef> providerRefs) {

    public Board {
        id = BoardsContract.requireText(id, "id");
        projectId = BoardsContract.requireText(projectId, "projectId");
        name = BoardsContract.requireText(name, "name");
        columns = BoardsContract.immutableList(columns, "columns");
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }
}
