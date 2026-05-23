package com.massimotter.weave.backend.boards.vikunja;

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

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * First provider adapter boundary for Vikunja. Vikunja words such as "project"
 * and "bucket" are translated here so the rest of Weave can keep provider-neutral
 * project, board, column, and task concepts.
 */
public final class VikunjaBoardsMapper {

    public WeaveProject toProject(VikunjaProjectSnapshot source) {
        return new WeaveProject(
                weaveId("project", source.id()),
                source.title(),
                source.archived() ? ProjectVisibility.PRIVATE : ProjectVisibility.WORKSPACE,
                List.of(),
                List.of(providerRef("project", source.id(), source.webUrl(), null, null)));
    }

    public Board toBoard(VikunjaProjectSnapshot source, List<BoardColumn> columns) {
        return new Board(
                weaveId("board", source.id()),
                weaveId("project", source.id()),
                source.title(),
                source.description(),
                columns,
                source.archived(),
                List.of(providerRef("project", source.id(), source.webUrl(), null, null)));
    }

    public BoardColumn toColumn(VikunjaBucketSnapshot source) {
        return new BoardColumn(
                weaveId("column", source.id()),
                weaveId("board", source.projectId()),
                source.title(),
                source.position(),
                semanticStatus(source.title()),
                source.limit(),
                List.of(providerRef("bucket", source.id(), null, null, null)));
    }

    public TaskItem toTask(VikunjaTaskSnapshot source) {
        Instant updatedAt = source.updatedAt() == null ? Instant.EPOCH : source.updatedAt();
        return new TaskItem(
                weaveId("task", source.id()),
                weaveId("board", source.projectId()),
                weaveId("column", source.bucketId()),
                source.title(),
                source.description(),
                source.done() ? TaskStatus.COMPLETED : TaskStatus.OPEN,
                source.position(),
                source.assigneeRefs(),
                source.labelRefs(),
                priority(source.priority()),
                source.startAt(),
                source.dueAt(),
                source.doneAt(),
                updatedAt,
                List.of(providerRef("task", source.id(), source.webUrl(), source.etag(), updatedAt)));
    }

    private ColumnSemanticStatus semanticStatus(String title) {
        String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
        if (normalized.contains("done") || normalized.contains("complete")) {
            return ColumnSemanticStatus.DONE;
        }
        if (normalized.contains("block")) {
            return ColumnSemanticStatus.BLOCKED;
        }
        if (normalized.contains("doing") || normalized.contains("progress")) {
            return ColumnSemanticStatus.IN_PROGRESS;
        }
        if (normalized.contains("archive")) {
            return ColumnSemanticStatus.ARCHIVED;
        }
        return ColumnSemanticStatus.NOT_STARTED;
    }

    private TaskPriority priority(Integer priority) {
        if (priority == null) {
            return null;
        }
        if (priority >= 4) {
            return TaskPriority.URGENT;
        }
        if (priority == 3) {
            return TaskPriority.HIGH;
        }
        if (priority == 1) {
            return TaskPriority.LOW;
        }
        return TaskPriority.NORMAL;
    }

    private ProviderRef providerRef(String type, long id, java.net.URI url, String etag, Instant lastSyncedAt) {
        return new ProviderRef(ProviderKind.VIKUNJA, type + ":" + id, url, null, etag, lastSyncedAt);
    }

    private String weaveId(String type, long id) {
        return "vikunja:" + type + ":" + id;
    }
}
