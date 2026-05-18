package com.massimotter.weave.backend.boards.openproject;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardColumn;
import com.massimotter.weave.backend.boards.domain.ColumnSemanticStatus;
import com.massimotter.weave.backend.boards.domain.ProjectVisibility;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.domain.WeaveProject;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenProject-first read-sync mapper. It translates OpenProject projects,
 * statuses, and work packages into Weave-owned Boards concepts while preserving
 * provider refs only for diagnostics/export/sync metadata.
 */
public final class OpenProjectBoardsMapper {

    public WeaveProject toProject(OpenProjectProjectSnapshot source) {
        return new WeaveProject(
                weaveId("project", source.id()),
                source.name(),
                source.archived() ? ProjectVisibility.PRIVATE : ProjectVisibility.WORKSPACE,
                List.of(),
                List.of(providerRef("project", source.id(), source.webUrl(), null, source.updatedAt())));
    }

    public Board toBoard(OpenProjectProjectSnapshot source, List<BoardColumn> columns) {
        return new Board(
                weaveId("board", source.id()),
                weaveId("project", source.id()),
                source.name(),
                source.description(),
                columns,
                source.archived(),
                List.of(providerRef("project", source.id(), source.webUrl(), null, source.updatedAt())));
    }

    public BoardColumn toColumn(long projectId, OpenProjectStatusSnapshot source) {
        return new BoardColumn(
                weaveId("status", source.id()),
                weaveId("board", projectId),
                source.name(),
                Math.max(0, source.position()),
                semanticStatus(source),
                null,
                List.of(providerRef("status", source.id(), source.webUrl(), null, null)));
    }

    public TaskItem toTask(OpenProjectWorkPackageSnapshot source, Map<Long, OpenProjectStatusSnapshot> statusesById) {
        OpenProjectStatusSnapshot status = statusesById.get(source.statusId());
        Instant updatedAt = source.updatedAt() == null ? Instant.EPOCH : source.updatedAt();
        boolean completed = status != null && status.closed();
        return new TaskItem(
                weaveId("work-package", source.id()),
                weaveId("board", source.projectId()),
                weaveId("status", source.statusId()),
                source.subject(),
                source.description(),
                completed ? TaskStatus.COMPLETED : TaskStatus.OPEN,
                Math.max(0, source.position()),
                source.assigneeRefs() == null ? List.of() : source.assigneeRefs(),
                source.labelRefs() == null ? List.of() : source.labelRefs(),
                priority(source.priority()),
                source.startAt(),
                source.dueAt(),
                completed ? source.closedAt() : null,
                updatedAt,
                List.of(providerRef("work-package", source.id(), source.webUrl(), source.lockVersion(), updatedAt)));
    }

    private ColumnSemanticStatus semanticStatus(OpenProjectStatusSnapshot status) {
        if (status.closed()) {
            return ColumnSemanticStatus.DONE;
        }
        String normalized = normalize(status.name());
        if (normalized.contains("block") || normalized.contains("impediment")) {
            return ColumnSemanticStatus.BLOCKED;
        }
        if (normalized.contains("progress") || normalized.contains("develop") || normalized.contains("doing")) {
            return ColumnSemanticStatus.IN_PROGRESS;
        }
        if (normalized.contains("reject") || normalized.contains("archive")) {
            return ColumnSemanticStatus.ARCHIVED;
        }
        return ColumnSemanticStatus.NOT_STARTED;
    }

    private TaskPriority priority(String priority) {
        String normalized = normalize(priority);
        if (normalized.contains("immediate") || normalized.contains("urgent")) {
            return TaskPriority.URGENT;
        }
        if (normalized.contains("high")) {
            return TaskPriority.HIGH;
        }
        if (normalized.contains("low")) {
            return TaskPriority.LOW;
        }
        return priority == null || priority.isBlank() ? null : TaskPriority.NORMAL;
    }

    private ProviderRef providerRef(String type, long id, URI url, String version, Instant lastSyncedAt) {
        return new ProviderRef(ProviderKind.OPEN_PROJECT, type + ":" + id, url, version, null, lastSyncedAt);
    }

    private String weaveId(String type, long id) {
        return "openproject:" + type + ":" + id;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
    }
}
