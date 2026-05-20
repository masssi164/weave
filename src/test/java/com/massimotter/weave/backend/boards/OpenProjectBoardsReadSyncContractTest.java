package com.massimotter.weave.backend.boards;

import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.ColumnSemanticStatus;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsMapper;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRuntimeGate;
import com.massimotter.weave.backend.boards.openproject.OpenProjectProjectSnapshot;
import com.massimotter.weave.backend.boards.openproject.OpenProjectStatusSnapshot;
import com.massimotter.weave.backend.boards.openproject.OpenProjectWorkPackageSnapshot;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenProjectBoardsReadSyncContractTest {

    @Test
    void openProjectIsPreferredReadSyncSeamButRuntimeStaysDisabled() {
        var repository = new OpenProjectBoardsRepository();

        var capabilities = repository.capabilities();

        assertThat(capabilities.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
        assertThat(capabilities.enabled()).isFalse();
        assertThat(capabilities.supported()).contains(
                BoardCapability.COMMENTS,
                BoardCapability.ATTACHMENTS,
                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                BoardCapability.INCREMENTAL_SYNC,
                BoardCapability.CUSTOM_FIELDS,
                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES);
        assertThat(capabilities.unsupported()).contains(BoardCapability.WEBHOOK_EVENTS, BoardCapability.CHECKLISTS);
        assertThat(capabilities.supportSafeSummary())
                .contains("OpenProject")
                .contains("read-only-first")
                .contains("fallback");
        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE);
                    assertThat(boardsError.details()).containsEntry("provider", "openproject");
                    assertThat(boardsError.details().get("missingGates"))
                            .contains("provider_runtime")
                            .contains("read_sync")
                            .contains("context_authorization");
                })
                .hasMessageContaining("read-sync")
                .hasMessageContaining("fail-closed");
    }

    @Test
    void openProjectGateRequiresContextAuthorizationBeforeReadSync() {
        var repository = new OpenProjectBoardsRepository(new OpenProjectBoardsRuntimeGate(
                true,
                true,
                false,
                false,
                false,
                "service-token"));

        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE);
                    assertThat(boardsError.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "list-projects")
                            .containsEntry("mode", "read_sync");
                    assertThat(boardsError.details().get("missingGates"))
                            .contains("context_authorization")
                            .doesNotContain("provider_runtime")
                            .doesNotContain("read_sync");
                });
    }

    @Test
    void openProjectGateRequiresBackendHeldServiceTokenAuthModeBeforeReadSync() {
        var repository = new OpenProjectBoardsRepository(new OpenProjectBoardsRuntimeGate(
                true,
                true,
                true,
                false,
                false,
                "api-token"),
                URI.create("https://openproject.example.test"),
                "secret-api-token",
                RestClient.builder());

        assertThat(repository.capabilities().enabled()).isFalse();
        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE);
                    assertThat(boardsError.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "list-projects")
                            .containsEntry("mode", "read_sync");
                    assertThat(boardsError.details().get("missingGates"))
                            .contains("provider_auth_mode_service_token")
                            .doesNotContain("context_authorization");
                });
    }

    @Test
    void openProjectWritesStayUnsupportedUntilAuditConsentPromotion() {
        var repository = new OpenProjectBoardsRepository(new OpenProjectBoardsRuntimeGate(
                true,
                true,
                true,
                false,
                false,
                "service-token"));

        assertThatThrownBy(() -> repository.completeTask("openproject:work-package:99"))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.UNSUPPORTED_CAPABILITY);
                    assertThat(boardsError.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "complete-task")
                            .containsEntry("mode", "write")
                            .containsEntry("missingGates", "provider_writes_disabled")
                            .containsEntry("providerWritesEnabled", "false");
                })
                .hasMessageContaining("writes remain disabled")
                .hasMessageContaining("audit");
    }

    @Test
    void enabledReadSyncFetchesProjectsStatusesAndWorkPackagesWithBackendHeldToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var repository = enabledRepository(builder);

        server.expect(requestTo("https://openproject.example.test/api/v3/projects/42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(projectJson(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("https://openproject.example.test/api/v3/statuses")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(statusesJson(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("https://openproject.example.test/api/v3/statuses")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(statusesJson(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("https://openproject.example.test/api/v3/work_packages")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(workPackagesJson(), MediaType.APPLICATION_JSON));

        var boards = repository.listBoards("openproject:project:42", null);
        var tasks = repository.listTasks("openproject:board:42", null);

        assertThat(repository.capabilities().enabled()).isTrue();
        assertThat(boards.items()).singleElement().satisfies(board -> {
            assertThat(board.id()).isEqualTo("openproject:board:42");
            assertThat(board.name()).isEqualTo("Apollo Launch");
            assertThat(board.columns()).extracting("semanticStatus")
                    .containsExactly(ColumnSemanticStatus.NOT_STARTED, ColumnSemanticStatus.IN_PROGRESS, ColumnSemanticStatus.DONE);
        });
        assertThat(tasks.items()).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo("openproject:work-package:99");
            assertThat(task.boardId()).isEqualTo("openproject:board:42");
            assertThat(task.columnId()).isEqualTo("openproject:status:3");
            assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.providerRefs()).singleElement().satisfies(ref -> {
                assertThat(ref.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
                assertThat(ref.externalId()).isEqualTo("work-package:99");
                assertThat(ref.version()).isEqualTo("17");
            });
        });
        assertThat(tasks.nextCursor()).isNull();
        server.verify();
    }

    @Test
    void enabledReadSyncMapsProviderHttpErrorsToSupportSafeBoardErrors() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var repository = enabledRepository(builder);
        server.expect(requestTo(containsString("https://openproject.example.test/api/v3/projects")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"raw upstream error that must not leak\"}"));

        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOfSatisfying(BoardsException.class, error -> {
                    assertThat(error.code()).isEqualTo(BoardsErrorCode.FORBIDDEN);
                    assertThat(error.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "list-projects")
                            .containsEntry("supportSafe", "true");
                    assertThat(error.getMessage()).doesNotContain("raw upstream");
                });
        server.verify();
    }

    @Test
    void enabledReadSyncUsesOpaqueSupportSafeCursorsForProviderPagination() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var repository = enabledRepository(builder);

        server.expect(requestTo(containsString("https://openproject.example.test/api/v3/projects")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(queryParam("pageSize", "2"))
                .andExpect(queryParam("offset", "1"))
                .andRespond(withSuccess(projectsPageJson(1, 2, 3, 42, "Apollo Launch"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("https://openproject.example.test/api/v3/projects")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(queryParam("pageSize", "2"))
                .andExpect(queryParam("offset", "3"))
                .andRespond(withSuccess(projectsPageJson(3, 1, 3, 43, "Hermes Ops"), MediaType.APPLICATION_JSON));

        var firstPage = repository.listProjects(new BoardQuery(null, 2));

        assertThat(firstPage.items()).singleElement().satisfies(project ->
                assertThat(project.id()).isEqualTo("openproject:project:42"));
        assertThat(firstPage.nextCursor())
                .startsWith("op:v1:")
                .isNotEqualTo("3")
                .doesNotContain("openproject.example.test")
                .doesNotContain("secret-api-token");

        var secondPage = repository.listProjects(new BoardQuery(firstPage.nextCursor(), 2));

        assertThat(secondPage.items()).singleElement().satisfies(project ->
                assertThat(project.id()).isEqualTo("openproject:project:43"));
        assertThat(secondPage.nextCursor()).isNull();
        server.verify();
    }

    @Test
    void enabledReadSyncRejectsRawProviderOffsetsAsInvalidCursors() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var repository = enabledRepository(builder);

        assertThatThrownBy(() -> repository.listProjects(new BoardQuery("3", 2)))
                .isInstanceOfSatisfying(BoardsException.class, error -> {
                    assertThat(error.code()).isEqualTo(BoardsErrorCode.VALIDATION);
                    assertThat(error.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "list-projects")
                            .containsEntry("reason", "cursor_validation")
                            .containsEntry("supportSafe", "true");
                    assertThat(error.getMessage()).doesNotContain("3");
                });
        server.verify();
    }

    @Test
    void mapperTurnsOpenProjectProjectsStatusesAndWorkPackagesIntoWeaveBoards() {
        var mapper = new OpenProjectBoardsMapper();
        Instant syncedAt = Instant.parse("2026-05-18T19:45:00Z");
        var project = new OpenProjectProjectSnapshot(
                42,
                "apollo",
                "Apollo Launch",
                "Imported from OpenProject read sync.",
                false,
                URI.create("https://openproject.example.test/projects/apollo"),
                syncedAt);
        var statusNew = new OpenProjectStatusSnapshot(1, "New", 0, false, null);
        var statusBlocked = new OpenProjectStatusSnapshot(2, "Blocked", 1, false, null);
        var statusClosed = new OpenProjectStatusSnapshot(3, "Closed", 2, true, null);

        var columns = List.of(
                mapper.toColumn(project.id(), statusNew),
                mapper.toColumn(project.id(), statusBlocked),
                mapper.toColumn(project.id(), statusClosed));
        var board = mapper.toBoard(project, columns);
        var task = mapper.toTask(new OpenProjectWorkPackageSnapshot(
                        99,
                        project.id(),
                        statusClosed.id(),
                        "Ship backend seam",
                        "Read-only first; no provider writes.",
                        7,
                        "High",
                        List.of("user:massimo"),
                        List.of("label:contract"),
                        null,
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-05-19T08:00:00Z"),
                        syncedAt,
                        URI.create("https://openproject.example.test/work_packages/99"),
                        "17"),
                Map.of(statusNew.id(), statusNew, statusBlocked.id(), statusBlocked, statusClosed.id(), statusClosed));

        assertThat(mapper.toProject(project).id()).isEqualTo("openproject:project:42");
        assertThat(board.id()).isEqualTo("openproject:board:42");
        assertThat(board.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
            assertThat(ref.externalId()).isEqualTo("project:42");
        });
        assertThat(columns).extracting("semanticStatus")
                .containsExactly(ColumnSemanticStatus.NOT_STARTED, ColumnSemanticStatus.BLOCKED, ColumnSemanticStatus.DONE);
        assertThat(task.id()).isEqualTo("openproject:work-package:99");
        assertThat(task.boardId()).isEqualTo("openproject:board:42");
        assertThat(task.columnId()).isEqualTo("openproject:status:3");
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
            assertThat(ref.externalId()).isEqualTo("work-package:99");
            assertThat(ref.version()).isEqualTo("17");
            assertThat(ref.lastSyncedAt()).isEqualTo(syncedAt);
        });
    }

    private OpenProjectBoardsRepository enabledRepository(RestClient.Builder builder) {
        return new OpenProjectBoardsRepository(
                new OpenProjectBoardsRuntimeGate(true, true, true, false, false, "service-token"),
                URI.create("https://openproject.example.test"),
                "secret-api-token",
                builder);
    }

    private String authHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("apikey:secret-api-token".getBytes(StandardCharsets.UTF_8));
    }

    private String projectJson() {
        return """
                {
                  "id": 42,
                  "identifier": "apollo",
                  "name": "Apollo Launch",
                  "description": {"raw": "Provider-backed read sync."},
                  "active": true,
                  "updatedAt": "2026-05-20T12:00:00Z"
                }
                """;
    }

    private String projectsPageJson(int offset, int count, int total, long projectId, String name) {
        return """
                {
                  "count": %d,
                  "total": %d,
                  "pageSize": 2,
                  "offset": %d,
                  "_embedded": {
                    "elements": [
                      {
                        "id": %d,
                        "identifier": "project-%d",
                        "name": "%s",
                        "description": {"raw": "Provider-backed read sync."},
                        "active": true,
                        "updatedAt": "2026-05-20T12:00:00Z"
                      }
                    ]
                  }
                }
                """.formatted(count, total, offset, projectId, projectId, name);
    }

    private String statusesJson() {
        return """
                {
                  "count": 3,
                  "total": 3,
                  "pageSize": 50,
                  "offset": 1,
                  "_embedded": {
                    "elements": [
                      {"id": 1, "name": "New", "position": 0, "isClosed": false},
                      {"id": 2, "name": "In progress", "position": 1, "isClosed": false},
                      {"id": 3, "name": "Closed", "position": 2, "isClosed": true}
                    ]
                  }
                }
                """;
    }

    private String workPackagesJson() {
        return """
                {
                  "count": 1,
                  "total": 1,
                  "pageSize": 50,
                  "offset": 1,
                  "_embedded": {
                    "elements": [
                      {
                        "id": 99,
                        "subject": "Ship backend seam",
                        "description": {"raw": "Read-only first; no provider writes."},
                        "position": 7,
                        "lockVersion": 17,
                        "dueDate": "2026-06-01",
                        "closedAt": "2026-05-19T08:00:00Z",
                        "updatedAt": "2026-05-20T12:00:00Z",
                        "_links": {
                          "project": {"href": "/api/v3/projects/42", "title": "Apollo Launch"},
                          "status": {"href": "/api/v3/statuses/3", "title": "Closed"},
                          "priority": {"href": "/api/v3/priorities/8", "title": "High"},
                          "assignee": {"href": "/api/v3/users/5", "title": "Massimo"}
                        }
                      }
                    ]
                  }
                }
                """;
    }
}
