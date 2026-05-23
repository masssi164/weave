package com.massimotter.weave.backend.boards;

import com.massimotter.weave.backend.boards.domain.ColumnSemanticStatus;
import com.massimotter.weave.backend.boards.domain.ProjectVisibility;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.vikunja.VikunjaBoardsMapper;
import com.massimotter.weave.backend.boards.vikunja.VikunjaBucketSnapshot;
import com.massimotter.weave.backend.boards.vikunja.VikunjaProjectSnapshot;
import com.massimotter.weave.backend.boards.vikunja.VikunjaTaskSnapshot;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VikunjaBoardsMapperTest {

    private final VikunjaBoardsMapper mapper = new VikunjaBoardsMapper();

    @Test
    void mapsVikunjaProjectIntoProviderNeutralProjectAndBoard() {
        var source = new VikunjaProjectSnapshot(
                42,
                "Launch Plan",
                "Coordinate active product follow-ups",
                false,
                URI.create("https://tasks.weave.local/projects/42"));

        var project = mapper.toProject(source);
        var board = mapper.toBoard(source, List.of());

        assertThat(project.id()).isEqualTo("vikunja:project:42");
        assertThat(project.name()).isEqualTo("Launch Plan");
        assertThat(project.visibility()).isEqualTo(ProjectVisibility.WORKSPACE);
        assertThat(project.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.VIKUNJA);
            assertThat(ref.externalId()).isEqualTo("project:42");
            assertThat(ref.externalUrl()).hasToString("https://tasks.weave.local/projects/42");
        });

        assertThat(board.id()).isEqualTo("vikunja:board:42");
        assertThat(board.projectId()).isEqualTo("vikunja:project:42");
        assertThat(board.name()).isEqualTo("Launch Plan");
        assertThat(board.description()).isEqualTo("Coordinate active product follow-ups");
        assertThat(board.archived()).isFalse();
    }

    @Test
    void mapsVikunjaBucketIntoSemanticColumnWithoutLeakingBucketVocabulary() {
        var blocked = mapper.toColumn(new VikunjaBucketSnapshot(7, 42, "Blocked", 2, 3));
        var progress = mapper.toColumn(new VikunjaBucketSnapshot(8, 42, "In progress", 1, null));

        assertThat(blocked.id()).isEqualTo("vikunja:column:7");
        assertThat(blocked.boardId()).isEqualTo("vikunja:board:42");
        assertThat(blocked.semanticStatus()).isEqualTo(ColumnSemanticStatus.BLOCKED);
        assertThat(blocked.wipLimit()).isEqualTo(3);
        assertThat(progress.semanticStatus()).isEqualTo(ColumnSemanticStatus.IN_PROGRESS);
        assertThat(blocked.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.VIKUNJA);
            assertThat(ref.externalId()).isEqualTo("bucket:7");
        });
    }

    @Test
    void mapsVikunjaTaskIntoProviderNeutralTaskAndPreservesProviderMetadata() {
        Instant updatedAt = Instant.parse("2026-05-14T10:00:00Z");
        Instant dueAt = Instant.parse("2026-05-20T12:00:00Z");
        var source = new VikunjaTaskSnapshot(
                99,
                42,
                7,
                "Write provider contract",
                "Keep it hidden until promoted",
                false,
                4,
                List.of("user:alice"),
                List.of("label:spec"),
                4,
                null,
                dueAt,
                null,
                updatedAt,
                "etag-99",
                URI.create("https://tasks.weave.local/tasks/99"));

        var task = mapper.toTask(source);

        assertThat(task.id()).isEqualTo("vikunja:task:99");
        assertThat(task.boardId()).isEqualTo("vikunja:board:42");
        assertThat(task.columnId()).isEqualTo("vikunja:column:7");
        assertThat(task.title()).isEqualTo("Write provider contract");
        assertThat(task.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(task.priority()).isEqualTo(TaskPriority.URGENT);
        assertThat(task.dueAt()).isEqualTo(dueAt);
        assertThat(task.updatedAt()).isEqualTo(updatedAt);
        assertThat(task.assigneeRefs()).containsExactly("user:alice");
        assertThat(task.labelRefs()).containsExactly("label:spec");
        assertThat(task.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.VIKUNJA);
            assertThat(ref.externalId()).isEqualTo("task:99");
            assertThat(ref.externalUrl()).hasToString("https://tasks.weave.local/tasks/99");
            assertThat(ref.etag()).isEqualTo("etag-99");
            assertThat(ref.lastSyncedAt()).isEqualTo(updatedAt);
        });
    }
}
