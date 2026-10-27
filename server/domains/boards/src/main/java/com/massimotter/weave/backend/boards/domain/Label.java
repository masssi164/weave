package com.massimotter.weave.backend.boards.domain;

import java.util.List;

public record Label(
        String id,
        String name,
        String color,
        String description,
        List<ProviderRef> providerRefs) {

    public Label {
        id = BoardsContract.requireText(id, "id");
        name = BoardsContract.requireText(name, "name");
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }
}
