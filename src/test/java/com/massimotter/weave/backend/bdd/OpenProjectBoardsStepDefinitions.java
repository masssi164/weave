package com.massimotter.weave.backend.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRuntimeGate;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsPreviewGuard;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.boards.BoardsPreviewResponse;
import com.massimotter.weave.backend.service.BoardsFacadeService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

public class OpenProjectBoardsStepDefinitions {

    private static final String BASE_URL = "https://openproject.example.test";
    private static final String API_TOKEN = "secret-api-token";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private OpenProjectBoardsRepository repository;
    private BoardsFacadeService service;
    private BoardsPreviewResponse response;
    private RuntimeException thrown;
    private boolean denyContext;
    private boolean openProjectShouldNotBeContacted;

    @Before
    public void reset() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        response = null;
        thrown = null;
        denyContext = false;
        openProjectShouldNotBeContacted = false;
    }

    @Given("the OpenProject Boards provider is disabled")
    public void theOpenProjectBoardsProviderIsDisabled() {
        ensureScenarioState();
        repository = new OpenProjectBoardsRepository();
        service = serviceFor(repository);
    }

    @Given("the OpenProject Boards provider is enabled with backend-held credentials")
    public void theOpenProjectBoardsProviderIsEnabledWithBackendHeldCredentials() {
        ensureScenarioState();
        repository = enabledRepository();
        service = serviceFor(repository);
    }

    @Given("OpenProject has a project {string} with a completed work package {string}")
    public void openProjectHasAProjectWithACompletedWorkPackage(String projectName, String taskTitle) {
        expectPreviewReadSync(projectName, taskTitle, false);
    }

    @Given("OpenProject has a second page of projects")
    public void openProjectHasASecondPageOfProjects() {
        expectPreviewReadSync("Apollo Launch", "Ship backend seam", true);
    }

    @Given("the workspace member has no Boards permission for the Context Space")
    public void theWorkspaceMemberHasNoBoardsPermissionForTheContextSpace() {
        denyContext = true;
        openProjectShouldNotBeContacted = true;
        if (server != null) {
            server.reset();
        }
    }

    @When("a workspace member previews Boards through Weave")
    public void aWorkspaceMemberPreviewsBoardsThroughWeave() {
        try {
            response = service.preview(jwt());
        } catch (RuntimeException error) {
            thrown = error;
        }
    }

    @When("a workspace member tries unsupported OpenProject provider actions")
    public void aWorkspaceMemberTriesUnsupportedOpenProjectProviderActions() {
        // Assertions for this action live in the following Then steps so each refusal is checked independently.
    }

    @Then("the Boards request fails with {string}")
    public void theBoardsRequestFailsWith(String code) {
        assertThat(thrown).isInstanceOf(ApiErrorException.class);
        assertThat(((ApiErrorException) thrown).code()).isEqualTo(code);
        assertThat(response).isNull();
    }

    @Then("the error is support-safe")
    public void theErrorIsSupportSafe() throws Exception {
        assertThat(thrown).isInstanceOf(ApiErrorException.class);
        ApiErrorException error = (ApiErrorException) thrown;
        String serialized = JSON.writeValueAsString(Map.of(
                "message", error.getMessage(),
                "code", error.code(),
                "details", error.details()));
        assertThat(serialized)
                .contains("boards")
                .doesNotContain(API_TOKEN)
                .doesNotContain("Authorization")
                .doesNotContain("raw upstream error")
                .doesNotContain(BASE_URL);
        if (error.details().containsKey("supportSafe")) {
            assertThat(error.details()).containsEntry("supportSafe", "true");
        }
    }

    @Then("the response does not leak provider secrets or raw OpenProject URLs")
    public void theResponseDoesNotLeakProviderSecretsOrRawOpenProjectUrls() throws Exception {
        Object target = response == null
                ? Map.of("message", thrown.getMessage(), "details", ((ApiErrorException) thrown).details())
                : response;
        String serialized = JSON.writeValueAsString(target);
        assertThat(serialized)
                .doesNotContain(API_TOKEN)
                .doesNotContain("Authorization")
                .doesNotContain(BASE_URL)
                .doesNotContain("/api/v3/")
                .doesNotContain("/work_packages/")
                .doesNotContain("/projects/");
    }

    @Then("Weave returns an OpenProject read-only Boards snapshot")
    public void weaveReturnsAnOpenProjectReadOnlyBoardsSnapshot() {
        assertThat(thrown).isNull();
        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo("openproject-read-sync-backend-facade");
        assertThat(response.capabilities().provider().contractName()).isEqualTo("openproject");
        assertThat(response.capabilities().enabled()).isTrue();
    }

    @Then("the snapshot contains board {string} and task {string}")
    public void theSnapshotContainsBoardAndTask(String boardName, String taskTitle) {
        assertThat(response.boards()).anySatisfy(board -> assertThat(board.name()).isEqualTo(boardName));
        assertThat(response.tasks()).anySatisfy(task -> {
            assertThat(task.title()).isEqualTo(taskTitle);
            assertThat(task.status().contractName()).isEqualTo("completed");
        });
    }

    @Then("sync metadata is support-safe and read-only")
    public void syncMetadataIsSupportSafeAndReadOnly() {
        assertThat(response.syncMetadata().provider()).isEqualTo("openproject");
        assertThat(response.syncMetadata().mode()).isEqualTo("read-only-sync");
        assertThat(response.syncMetadata().readOnly()).isTrue();
        assertThat(response.syncMetadata().contextScoped()).isTrue();
        assertThat(response.syncMetadata().supportSafe()).isTrue();
    }

    @Then("OpenProject was not contacted")
    public void openProjectWasNotContacted() {
        assertThat(openProjectShouldNotBeContacted).isTrue();
        server.verify();
    }

    @Then("sync metadata contains an opaque OpenProject cursor")
    public void syncMetadataContainsAnOpaqueOpenProjectCursor() {
        assertThat(response.syncMetadata().nextCursors()).containsKey("projects");
        assertThat(response.syncMetadata().nextCursors().get("projects"))
                .startsWith("op:v1:")
                .doesNotContain("offset")
                .doesNotContain(BASE_URL)
                .doesNotContain(API_TOKEN);
    }

    @Then("provider writes are refused support-safely")
    public void providerWritesAreRefusedSupportSafely() {
        assertThat(repository.capabilities().unsupported()).contains(
                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES);
        assertThatThrownBy(() -> repository.createTask(new CreateTaskCommand(
                "openproject:board:42",
                "openproject:status:1",
                "Provider write attempt",
                null,
                java.util.List.of(),
                java.util.List.of(),
                null)))
                .isInstanceOfSatisfying(BoardsException.class, error -> {
                    assertThat(error.code()).isEqualTo(BoardsErrorCode.UNSUPPORTED_CAPABILITY);
                    assertThat(error.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "create-task")
                            .containsEntry("supportSafe", "true");
                    assertThat(error.getMessage()).doesNotContain(API_TOKEN).doesNotContain(BASE_URL);
                });
    }

    @Then("comments attachments archive and agent automation are refused support-safely")
    public void commentsAttachmentsArchiveAndAgentAutomationAreRefusedSupportSafely() {
        assertThat(repository.capabilities().unsupported()).contains(
                BoardCapability.COMMENTS,
                BoardCapability.ATTACHMENTS,
                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                BoardCapability.WEBHOOK_EVENTS);
        assertThatThrownBy(() -> repository.listComments("openproject:work-package:99", BoardQuery.firstPage()))
                .isInstanceOfSatisfying(BoardsException.class, error -> {
                    assertThat(error.code()).isEqualTo(BoardsErrorCode.UNSUPPORTED_CAPABILITY);
                    assertThat(error.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("capability", "comments")
                            .containsEntry("supportSafe", "true");
                });
        assertThatThrownBy(() -> repository.listAttachments("openproject:work-package:99", BoardQuery.firstPage()))
                .isInstanceOfSatisfying(BoardsException.class, error -> {
                    assertThat(error.code()).isEqualTo(BoardsErrorCode.UNSUPPORTED_CAPABILITY);
                    assertThat(error.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("capability", "attachments")
                            .containsEntry("supportSafe", "true");
                });
    }

    private BoardsFacadeService serviceFor(OpenProjectBoardsRepository boardsRepository) {
        return new BoardsFacadeService(
                new BoardsPreviewGuard(true),
                boardsRepository,
                request -> denyContext
                        ? ContextAuthorizationDecision.deny("no matching context membership")
                        : ContextAuthorizationDecision.allow("context membership matched"));
    }

    private void ensureScenarioState() {
        if (restClientBuilder == null || server == null) {
            reset();
        }
    }

    private OpenProjectBoardsRepository enabledRepository() {
        return new OpenProjectBoardsRepository(
                new OpenProjectBoardsRuntimeGate(true, true, true, false, false, "service-token"),
                URI.create(BASE_URL),
                API_TOKEN,
                restClientBuilder);
    }

    private void expectPreviewReadSync(String projectName, String taskTitle, boolean secondPage) {
        int total = secondPage ? 2 : 1;
        server.expect(requestTo(containsString(BASE_URL + "/api/v3/projects")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andExpect(queryParam("offset", "1"))
                .andRespond(withSuccess(projectsPageJson(projectName, total), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v3/projects/42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(projectJson(projectName), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(BASE_URL + "/api/v3/statuses")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(statusesJson(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(BASE_URL + "/api/v3/statuses")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(statusesJson(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString(BASE_URL + "/api/v3/work_packages")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authHeader()))
                .andRespond(withSuccess(workPackagesJson(taskTitle), MediaType.APPLICATION_JSON));
    }

    private String authHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString(("apikey:" + API_TOKEN).getBytes(StandardCharsets.UTF_8));
    }

    private Jwt jwt() {
        Instant now = Instant.parse("2026-05-20T18:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("weave_tenant_id", "tenant-acme")
                .claim("weave_context_id", "ctx-product-channel")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private String projectsPageJson(String projectName, int total) {
        return """
                {
                  "count": 1,
                  "total": %d,
                  "pageSize": 50,
                  "offset": 1,
                  "_embedded": {"elements": [%s]}
                }
                """.formatted(total, projectElement(projectName));
    }

    private String projectJson(String projectName) {
        return projectElement(projectName);
    }

    private String projectElement(String projectName) {
        return """
                {
                  "id": 42,
                  "identifier": "apollo",
                  "name": "%s",
                  "description": {"raw": "Provider-backed read sync."},
                  "active": true,
                  "updatedAt": "2026-05-20T12:00:00Z"
                }
                """.formatted(projectName);
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

    private String workPackagesJson(String taskTitle) {
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
                        "subject": "%s",
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
                """.formatted(taskTitle);
    }
}
