package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardColumn;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.Label;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskAttachment;
import com.massimotter.weave.backend.boards.domain.TaskComment;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import com.massimotter.weave.backend.boards.local.LocalPreviewBoardsRepository;
import com.massimotter.weave.backend.boards.port.BoardPage;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsPreviewGuard;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.port.MoveTaskCommand;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardsFacadeServiceTest {

    @Test
    void previewRuntimeFailsClosedWhenFeatureFlagIsDisabled() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsPreviewGuard(false),
                new LocalPreviewBoardsRepository(),
                request -> ContextAuthorizationDecision.allow("test allow"));

        assertThatThrownBy(() -> service.preview(jwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("boards-provider_unavailable");
                    assertThat(error.details()).containsEntry("module", "boards");
                    assertThat(error.details()).containsEntry("preview", true);
                    assertThat(error.details()).containsEntry("releaseStatus", "active-feature-gated-preview");
                });
    }

    @Test
    void previewRuntimeFailsClosedWhenContextAuthorizationDeniesAccess() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsPreviewGuard(true),
                new LocalPreviewBoardsRepository(),
                request -> ContextAuthorizationDecision.deny("no matching context membership"));

        assertThatThrownBy(() -> service.preview(jwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo("boards-forbidden");
                    assertThat(error.details()).containsEntry("module", "boards");
                    assertThat(error.details()).containsEntry("preview", true);
                    assertThat(error.details()).containsEntry("reason", "no matching context membership");
                    assertThat(error.details()).containsEntry("contextId", "workspace-default");
                    assertThat(error.details()).containsEntry("permission", "view");
                });
    }

    @Test
    void createTaskRequiresEditPermissionForDefaultWorkspaceContext() {
        java.util.concurrent.atomic.AtomicReference<com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsPreviewGuard(true),
                new LocalPreviewBoardsRepository(),
                request -> {
                    captured.set(request);
                    return ContextAuthorizationDecision.deny("edit denied");
                });

        var createRequest = new com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest(
                "local-column-todo",
                "Write acceptance evidence",
                null,
                java.util.List.of(),
                java.util.List.of(),
                null);

        assertThatThrownBy(() -> service.createTask(jwt(), "local-board-1", createRequest))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.details()).containsEntry("permission", "edit");
                });
        assertThat(captured.get().tenantId()).isEqualTo("tenant-default");
        assertThat(captured.get().contextId()).isEqualTo("workspace-default");
        assertThat(captured.get().principalRef()).isEqualTo("user:user-123");
        assertThat(captured.get().permission().name()).isEqualTo("EDIT");
    }

    @Test
    void previewSourceReflectsOpenProjectReadSyncWhenProviderAdapterIsSelected() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsPreviewGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT),
                request -> ContextAuthorizationDecision.allow("test allow"));

        var response = service.preview(jwt());

        assertThat(response.source()).isEqualTo("openproject-read-sync-backend-facade");
        assertThat(response.capabilities().provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
        assertThat(response.syncMetadata().provider()).isEqualTo("openproject");
        assertThat(response.syncMetadata().mode()).isEqualTo("read-only-sync");
        assertThat(response.syncMetadata().readOnly()).isTrue();
        assertThat(response.syncMetadata().contextScoped()).isTrue();
        assertThat(response.syncMetadata().supportSafe()).isTrue();
        assertThat(response.projects()).isEmpty();
        assertThat(response.boards()).isEmpty();
        assertThat(response.tasks()).isEmpty();
    }

    @Test
    void previewIncludesOnlyOpaqueSupportSafeAdapterCursorsInSyncMetadata() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsPreviewGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT, "op:v1:c3VwcG9ydC1zYWZl"),
                request -> ContextAuthorizationDecision.allow("test allow"));

        var response = service.preview(jwt());

        assertThat(response.syncMetadata().nextCursors())
                .containsEntry("projects", "op:v1:c3VwcG9ydC1zYWZl");
        assertThat(response.syncMetadata().nextCursors().get("projects"))
                .doesNotContain("https://")
                .doesNotContain("secret")
                .doesNotContain("token");
    }

    @Test
    void previewFailsClosedInsteadOfLeakingUnsafeProviderCursor() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsPreviewGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT, "https://openproject.example.test/page?token=secret"),
                request -> ContextAuthorizationDecision.allow("test allow"));

        assertThatThrownBy(() -> service.preview(jwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("boards-validation");
                    assertThat(error.getMessage()).doesNotContain("openproject.example")
                            .doesNotContain("secret");
                    assertThat(error.details())
                            .containsEntry("cursorKey", "projects")
                            .containsEntry("supportSafe", "true");
                });
    }

    private Jwt jwt() {
        Instant now = Instant.parse("2026-05-19T05:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private static final class EmptyBoardsRepository implements BoardsRepository {
        private final BoardProviderCapabilities capabilities;
        private final String projectNextCursor;

        private EmptyBoardsRepository(ProviderKind provider) {
            this(provider, null);
        }

        private EmptyBoardsRepository(ProviderKind provider, String projectNextCursor) {
            this.projectNextCursor = projectNextCursor;
            this.capabilities = new BoardProviderCapabilities(
                    provider,
                    true,
                    Set.of(),
                    Set.of(),
                    "Support-safe empty repository fixture.");
        }

        @Override public BoardProviderCapabilities capabilities() { return capabilities; }
        @Override public BoardPage<WeaveProject> listProjects(BoardQuery query) { return new BoardPage<>(List.of(), projectNextCursor); }
        @Override public BoardPage<Board> listBoards(String projectId, BoardQuery query) { return BoardPage.singlePage(List.of()); }
        @Override public Optional<Board> findBoard(String boardId) { return Optional.empty(); }
        @Override public BoardPage<BoardColumn> listColumns(String boardId, BoardQuery query) { return BoardPage.singlePage(List.of()); }
        @Override public BoardPage<TaskItem> listTasks(String boardId, TaskQuery query) { return BoardPage.singlePage(List.of()); }
        @Override public BoardPage<Label> listLabels(String boardId, BoardQuery query) { return BoardPage.singlePage(List.of()); }
        @Override public BoardPage<TaskComment> listComments(String taskId, BoardQuery query) { return BoardPage.singlePage(List.of()); }
        @Override public BoardPage<TaskAttachment> listAttachments(String taskId, BoardQuery query) { return BoardPage.singlePage(List.of()); }
        @Override public TaskItem createTask(CreateTaskCommand command) { throw new UnsupportedOperationException(); }
        @Override public TaskItem moveTask(MoveTaskCommand command) { throw new UnsupportedOperationException(); }
        @Override public TaskItem completeTask(String taskId) { throw new UnsupportedOperationException(); }
    }
}
