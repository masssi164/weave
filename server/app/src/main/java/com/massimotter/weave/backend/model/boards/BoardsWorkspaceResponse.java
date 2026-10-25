package com.massimotter.weave.backend.model.boards;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import java.util.List;

public record BoardsWorkspaceResponse(
        boolean workspace,
        String releaseStatus,
        String source,
        BoardProviderCapabilities capabilities,
        BoardsSyncMetadataResponse syncMetadata,
        List<WeaveProject> projects,
        List<Board> boards,
        List<TaskItem> tasks) {

    public BoardsWorkspaceResponse {
        projects = List.copyOf(projects);
        boards = List.copyOf(boards);
        tasks = List.copyOf(tasks);
    }
}
