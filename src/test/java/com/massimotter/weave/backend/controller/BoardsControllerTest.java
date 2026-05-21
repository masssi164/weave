package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.BoardsRuntimeConfiguration;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.service.BoardsFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BoardsController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        BoardsRuntimeConfiguration.class,
        BoardsFacadeService.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.boards.preview.runtime-enabled=true",
        "weave.context.authorization.principal-claim=preferred_username"
})
@EnableConfigurationProperties(ContextAuthorizationProperties.class)
class BoardsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @Test
    void boardsPreviewRequiresAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/boards/preview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void boardsPreviewReturnsProviderNeutralLocalFacadeSnapshot() throws Exception {
        allowBoardsPermission(ContextPermission.VIEW);

        mockMvc.perform(get("/api/boards/preview")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.releaseStatus").value("active-feature-gated-preview"))
                .andExpect(jsonPath("$.source").value("local-preview-backend-facade"))
                .andExpect(jsonPath("$.capabilities.enabled").value(true))
                .andExpect(jsonPath("$.syncMetadata.provider").value("in-memory"))
                .andExpect(jsonPath("$.syncMetadata.mode").value("local-preview-backend-facade"))
                .andExpect(jsonPath("$.syncMetadata.readOnly").value(true))
                .andExpect(jsonPath("$.syncMetadata.contextScoped").value(true))
                .andExpect(jsonPath("$.syncMetadata.supportSafe").value(true))
                .andExpect(jsonPath("$.syncMetadata.nextCursors").isMap())
                .andExpect(jsonPath("$.syncMetadata.lastSyncedAt").value("2026-05-14T18:00:00Z"))
                .andExpect(jsonPath("$.projects[0].id").value("local-project-1"))
                .andExpect(jsonPath("$.boards[0].id").value("local-board-1"))
                .andExpect(jsonPath("$.boards[0].columns[0].semanticStatus").value("not_started"))
                .andExpect(jsonPath("$.tasks.length()").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.tasks[0].providerRefs[0].provider").value("in-memory"));
    }

    @Test
    void boardsPreviewSupportsCreateMoveAndCompleteWithoutDragOnlyFlow() throws Exception {
        allowBoardsPermission(ContextPermission.EDIT);
        String createPayload = """
                {
                  "columnId": "local-column-todo",
                  "title": "Write acceptance evidence",
                  "description": "Created through the backend Boards preview facade.",
                  "assigneeRefs": ["workspace:member"],
                  "labelRefs": ["evidence"]
                }
                """;

        String response = mockMvc.perform(post("/api/boards/local-board-1/tasks")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boardId").value("local-board-1"))
                .andExpect(jsonPath("$.columnId").value("local-column-todo"))
                .andExpect(jsonPath("$.title").value("Write acceptance evidence"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String taskId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(post("/api/boards/tasks/{taskId}/move", taskId)
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetColumnId\":\"local-column-active\",\"targetPosition\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.columnId").value("local-column-active"));

        mockMvc.perform(post("/api/boards/tasks/{taskId}/complete", taskId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.columnId").value("local-column-done"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void boardsPreviewUsesSupportSafeErrorsForUnknownTasks() throws Exception {
        allowBoardsPermission(ContextPermission.EDIT);

        mockMvc.perform(post("/api/boards/tasks/missing-task/complete")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("boards-not_found"))
                .andExpect(jsonPath("$.details.module").value("boards"))
                .andExpect(jsonPath("$.details.preview").value(true))
                .andExpect(jsonPath("$.details.resource").value("task"));
    }

    @Test
    void boardsPreviewFailsClosedWhenContextAuthorizationDeniesAccess() throws Exception {
        when(contextAuthorizationPort.check(argThat(request ->
                request != null
                        && "tenant-default".equals(request.tenantId())
                        && "workspace-default".equals(request.contextId())
                        && "user:test".equals(request.principalRef())
                        && request.permission() == ContextPermission.VIEW)))
                .thenReturn(ContextAuthorizationDecision.deny("no matching context membership"));

        mockMvc.perform(get("/api/boards/preview")
                        .with(workspaceJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("boards-forbidden"))
                .andExpect(jsonPath("$.details.module").value("boards"))
                .andExpect(jsonPath("$.details.preview").value(true))
                .andExpect(jsonPath("$.details.contextId").value("workspace-default"))
                .andExpect(jsonPath("$.details.permission").value("view"));
    }

    private void allowBoardsPermission(ContextPermission permission) {
        when(contextAuthorizationPort.check(argThat(request ->
                request != null
                        && "tenant-default".equals(request.tenantId())
                        && "workspace-default".equals(request.contextId())
                        && "user:test".equals(request.principalRef())
                        && request.permission() == permission)))
                .thenReturn(ContextAuthorizationDecision.allow("test allow"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "test")
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("aud", java.util.List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
