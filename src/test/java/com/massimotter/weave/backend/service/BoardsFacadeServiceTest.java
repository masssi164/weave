package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.boards.local.LocalPreviewBoardsRepository;
import com.massimotter.weave.backend.boards.port.BoardsPreviewGuard;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import java.time.Instant;
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

    private Jwt jwt() {
        Instant now = Instant.parse("2026-05-19T05:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
