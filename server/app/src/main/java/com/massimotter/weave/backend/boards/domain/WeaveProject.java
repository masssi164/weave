package com.massimotter.weave.backend.boards.domain;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record WeaveProject(
        String id,
        String name,
        ProjectVisibility visibility,
        List<String> memberRefs,
        List<ProviderRef> providerRefs) {

    public WeaveProject {
        id = BoardsContract.requireText(id, "id");
        name = BoardsContract.requireText(name, "name");
        visibility = requireNonNull(visibility, "visibility must not be null");
        memberRefs = BoardsContract.immutableList(memberRefs, "memberRefs");
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }
}
