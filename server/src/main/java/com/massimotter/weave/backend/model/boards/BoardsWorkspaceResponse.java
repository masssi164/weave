package com.massimotter.weave.backend.model.boards;

import java.util.List;

public record BoardsWorkspaceResponse(
        boolean workspace,
        String releaseStatus,
        String source,
        BoardsSyncMetadataResponse syncMetadata,
        List<BoardsProjectResponse> projects,
        List<BoardsBoardResponse> boards,
        List<BoardsTaskResponse> tasks) {

    public BoardsWorkspaceResponse {
        projects = List.copyOf(projects);
        boards = List.copyOf(boards);
        tasks = List.copyOf(tasks);
    }
}
