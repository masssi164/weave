package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardColumn;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.Label;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskAttachment;
import com.massimotter.weave.backend.boards.domain.TaskComment;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.boards.port.BoardPage;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsRuntimeGuard;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.port.MoveTaskCommand;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardsFacadeServiceTest {

    @Test
    void workspaceRuntimeFailsClosedWhenFeatureFlagIsDisabled() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(false),
                new LocalWorkspaceBoardsRepository(),
                request -> ContextAuthorizationDecision.allow("test allow"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> service.workspace(jwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(503);
                    assertThat(error.code()).isEqualTo("boards-provider_unavailable");
                    assertThat(error.details()).containsEntry("module", "boards");
                    assertThat(error.details()).containsEntry("workspace", true);
                    assertThat(error.details()).containsEntry("releaseStatus", "active-dogfood-production");
                });
    }

    @Test
    void workspaceRuntimeFailsClosedWhenContextAuthorizationDeniesAccess() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new LocalWorkspaceBoardsRepository(),
                request -> ContextAuthorizationDecision.deny("no matching context membership"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> service.workspace(jwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo("boards-forbidden");
                    assertThat(error.details()).containsEntry("module", "boards");
                    assertThat(error.details()).containsEntry("workspace", true);
                    assertThat(error.details()).containsEntry("reason", "no matching context membership");
                    assertThat(error.details()).containsEntry("contextId", "workspace-default");
                    assertThat(error.details()).containsEntry("permission", "view");
                });
    }

    @Test
    void workspaceUsesJwtTenantAndContextClaimsForContextBoundProviderReads() {
        java.util.concurrent.atomic.AtomicReference<com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT),
                request -> {
                    captured.set(request);
                    return ContextAuthorizationDecision.allow("context membership matched");
                },
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        service.workspace(jwtWithContext("tenant-acme", "ctx-product-channel"));

        assertThat(captured.get().tenantId()).isEqualTo("tenant-acme");
        assertThat(captured.get().contextId()).isEqualTo("ctx-product-channel");
        assertThat(captured.get().principalRef()).isEqualTo("user:user-123");
        assertThat(captured.get().permission().name()).isEqualTo("VIEW");
    }

    @Test
    void createTaskRequiresEditPermissionForDefaultWorkspaceContext() {
        java.util.concurrent.atomic.AtomicReference<com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new LocalWorkspaceBoardsRepository(),
                request -> {
                    captured.set(request);
                    return ContextAuthorizationDecision.deny("edit denied");
                },
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

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
    void createTaskRequiresEffectivePolicyGrantBeforeContextAuthorization() {
        java.util.concurrent.atomic.AtomicBoolean contextChecked = new java.util.concurrent.atomic.AtomicBoolean(false);
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new LocalWorkspaceBoardsRepository(),
                request -> {
                    contextChecked.set(true);
                    return ContextAuthorizationDecision.allow("context would allow");
                },
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        var createRequest = new com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest(
                "local-column-todo",
                "Write acceptance evidence",
                null,
                java.util.List.of(),
                java.util.List.of(),
                null);

        assertThatThrownBy(() -> service.createTask(jwtWithoutGroups(), "local-board-1", createRequest))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo("capability-policy-blocked");
                    assertThat(error.details()).containsEntry("requiredCapability", "boards.update_task");
                    assertThat(error.details()).containsEntry("diagnosticsRedacted", true);
                });
        assertThat(contextChecked).isFalse();
    }

    @Test
    void taskWritesPublishSupportSafeWorkspaceAuditEvents() {
        var auditPublisher = new InMemoryAuditEventPublisher();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new LocalWorkspaceBoardsRepository(),
                request -> ContextAuthorizationDecision.allow("edit allowed"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                auditPublisher);

        var created = service.createTask(jwt(), "local-board-1",
                new com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest(
                        "local-column-todo",
                        "Write acceptance evidence",
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null));
        var moved = service.moveTask(jwt(), created.id(),
                new com.massimotter.weave.backend.model.boards.BoardsMoveTaskRequest(
                        "local-column-active",
                        0));
        service.completeTask(jwt(), moved.id());

        assertThat(auditPublisher.events()).hasSize(3);
        assertThat(auditPublisher.events()).extracting(event -> event.action()).containsExactly(
                AuditAction.BOARD_TASK_CREATED,
                AuditAction.BOARD_TASK_MOVED,
                AuditAction.BOARD_TASK_COMPLETED);
        assertThat(auditPublisher.events()).allSatisfy(event -> {
            assertThat(event.sourceRef()).isEqualTo("weave:boards-workspace");
            assertThat(event.tenantId()).isEqualTo("tenant-default");
            assertThat(event.contextId()).isEqualTo("workspace-default");
            assertThat(event.payload()).containsEntry("supportSafe", true);
        });
    }

    @Test
    void preWriteAuditRedactsUnsafeResourceReferencesBeforeRepositoryValidation() {
        var auditPublisher = new InMemoryAuditEventPublisher();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new LocalWorkspaceBoardsRepository(),
                request -> ContextAuthorizationDecision.allow("edit allowed"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                auditPublisher);

        assertThatThrownBy(() -> service.moveTask(jwt(), "https://provider.example/tasks/42?token=secret",
                new com.massimotter.weave.backend.model.boards.BoardsMoveTaskRequest(
                        "https://provider.example/columns/1?token=secret",
                        0)))
                .isInstanceOf(ApiErrorException.class);

        assertThat(auditPublisher.events()).hasSize(1);
        assertThat(auditPublisher.events().get(0).idempotencyKey())
                .contains("[redacted:unsafe-ref]")
                .doesNotContain("provider.example")
                .doesNotContain("secret");
        assertThat(auditPublisher.events().get(0).payload().toString())
                .contains("[redacted:unsafe-ref]")
                .doesNotContain("provider.example")
                .doesNotContain("secret");
    }

    @Test
    void updateStatusFailsClosedBeforeMutationWhenAuditPublisherIsMissing() {
        LocalWorkspaceBoardsRepository repository = new LocalWorkspaceBoardsRepository();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                repository,
                request -> ContextAuthorizationDecision.allow("edit allowed"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                null);

        assertThat(task(repository, "local-task-contract").status()).isEqualTo(TaskStatus.OPEN);

        assertThatThrownBy(() -> service.updateTaskStatus(jwt(), "local-task-contract",
                new com.massimotter.weave.backend.model.boards.BoardsUpdateTaskStatusRequest("blocked", null)))
                .isInstanceOf(AuditRequiredException.class);

        assertThat(task(repository, "local-task-contract").status()).isEqualTo(TaskStatus.OPEN);
        assertThat(task(repository, "local-task-contract").columnId()).isEqualTo("local-column-todo");
    }

    @Test
    void linkDecisionFailsClosedBeforeMutationWhenAuditPublisherIsMissing() {
        LocalWorkspaceBoardsRepository repository = new LocalWorkspaceBoardsRepository();
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                repository,
                request -> ContextAuthorizationDecision.allow("edit allowed"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                null);

        assertThat(task(repository, "local-task-contract").decisionRefs()).isEmpty();

        assertThatThrownBy(() -> service.linkDecision(jwt(), "local-task-contract",
                new com.massimotter.weave.backend.model.boards.BoardsLinkDecisionRequest("decision:release-v0.1")))
                .isInstanceOf(AuditRequiredException.class);

        assertThat(task(repository, "local-task-contract").decisionRefs()).isEmpty();
    }

    @Test
    void workspaceSourceReflectsOpenProjectReadSyncWhenProviderAdapterIsSelected() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT),
                request -> ContextAuthorizationDecision.allow("test allow"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        var response = service.workspace(jwt());

        assertThat(response.source()).isEqualTo("openproject-workspace-sync-backend-facade");
        assertThat(response.capabilities().provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
        assertThat(response.syncMetadata().provider()).isEqualTo("openproject");
        assertThat(response.syncMetadata().mode()).isEqualTo("workspace-sync");
        assertThat(response.syncMetadata().userWriteAudited()).isTrue();
        assertThat(response.syncMetadata().contextScoped()).isTrue();
        assertThat(response.syncMetadata().supportSafe()).isTrue();
        assertThat(response.projects()).isEmpty();
        assertThat(response.boards()).isEmpty();
        assertThat(response.tasks()).isEmpty();
    }

    @Test
    void workspaceIncludesOnlyOpaqueSupportSafeAdapterCursorsInSyncMetadata() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT, "op:v1:c3VwcG9ydC1zYWZl"),
                request -> ContextAuthorizationDecision.allow("test allow"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        var response = service.workspace(jwt());

        assertThat(response.syncMetadata().nextCursors())
                .containsEntry("projects", "op:v1:c3VwcG9ydC1zYWZl");
        assertThat(response.syncMetadata().nextCursors().get("projects"))
                .doesNotContain("https://")
                .doesNotContain("secret")
                .doesNotContain("token");
    }

    @Test
    void workspaceFailsClosedInsteadOfLeakingUnsafeProviderCursor() {
        BoardsFacadeService service = new BoardsFacadeService(
                new BoardsRuntimeGuard(true),
                new EmptyBoardsRepository(ProviderKind.OPEN_PROJECT, "https://openproject.example.test/page?token=secret"),
                request -> ContextAuthorizationDecision.allow("test allow"),
                contextAuthorizationProperties(),
                workspaceCapabilityService(),
                new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> service.workspace(jwt()))
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

    private TaskItem task(LocalWorkspaceBoardsRepository repository, String taskId) {
        return repository.listTasks("local-board-1", TaskQuery.all()).items().stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow();
    }

    private ContextAuthorizationProperties contextAuthorizationProperties() {
        return new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return new WorkspaceCapabilityService(
                properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }

    private Jwt jwt() {
        Instant now = Instant.parse("2026-05-19T05:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("resource_access", java.util.Map.of("weave-app", java.util.Map.of("roles", java.util.List.of("member"))))
                .claim("groups", java.util.List.of("/weave-board-editors"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt jwtWithoutGroups() {
        Instant now = Instant.parse("2026-05-19T05:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("resource_access", java.util.Map.of("weave-app", java.util.Map.of("roles", java.util.List.of("member"))))
                .claim("groups", java.util.List.of())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt jwtWithContext(String tenantId, String contextId) {
        Instant now = Instant.parse("2026-05-19T05:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", tenantId)
                .claim("weave_context_id", contextId)
                .claim("resource_access", java.util.Map.of("weave-app", java.util.Map.of("roles", java.util.List.of("member"))))
                .claim("groups", java.util.List.of("/weave-board-editors"))
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
        @Override public TaskItem updateTaskStatus(String taskId, TaskStatus status, String targetColumnId) { throw new UnsupportedOperationException(); }
        @Override public TaskItem linkDecision(String taskId, String decisionRef) { throw new UnsupportedOperationException(); }
    }
}
