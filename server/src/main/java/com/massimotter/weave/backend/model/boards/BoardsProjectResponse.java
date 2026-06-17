package com.massimotter.weave.backend.model.boards;

import com.massimotter.weave.backend.boards.domain.ProjectVisibility;
import java.util.List;

public record BoardsProjectResponse(
        String id,
        String name,
        ProjectVisibility visibility,
        List<String> memberRefs,
        String mappingRef) {

    public BoardsProjectResponse {
        memberRefs = memberRefs == null ? List.of() : List.copyOf(memberRefs);
    }
}
