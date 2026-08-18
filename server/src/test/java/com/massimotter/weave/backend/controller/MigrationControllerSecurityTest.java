package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.migration.MigrationApplyGateResponse;
import com.massimotter.weave.backend.model.migration.MigrationDryRunResponse;
import com.massimotter.weave.backend.service.migration.MigrationApplyGateService;
import com.massimotter.weave.backend.service.migration.MigrationDryRunService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MigrationController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave"
})
class MigrationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private MigrationDryRunService migrationDryRunService;

    @MockitoBean
    private MigrationApplyGateService migrationApplyGateService;

    @Test
    void migrationDryRunRejectsWorkspaceMembersWithoutAdminOperatorRole() throws Exception {
        mockMvc.perform(post("/api/migration/dry-runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRunPayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void migrationApplyGateRejectsWorkspaceMembersWithoutOwnerOrAdminRole() throws Exception {
        mockMvc.perform(post("/api/migration/apply-gates")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyGatePayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void migrationDryRunAllowsAdminControlPlaneCallers() throws Exception {
        when(migrationDryRunService.dryRun(any())).thenReturn(dryRunResponse());

        mockMvc.perform(post("/api/migration/dry-runs")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                                new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRunPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("migration-chat-001"))
                .andExpect(jsonPath("$.supportSafe").value(true));
    }

    @Test
    void migrationApplyGateAllowsOwnerControlPlaneCallers() throws Exception {
        when(migrationApplyGateService.evaluate(any())).thenReturn(new MigrationApplyGateResponse(
                "migration-chat-001",
                "chat",
                "blocked",
                false,
                true,
                true,
                List.of("dryRunReportRef"),
                List.of("dryRunReportRef"),
                List.of("apply blocked until current server-side dry-run evidence exists for this run and domain"),
                List.of("Expose only the support-safe evidence bundle to admins and reviewers."),
                new MigrationApplyGateResponse.SupportSafeEvidenceBundle(
                        "migration-chat-001", "chat", "blocked", Map.of(), List.of(), List.of(), List.of(), List.of(), "support_safe")));

        mockMvc.perform(post("/api/migration/apply-gates")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                                new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyGatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("migration-chat-001"))
                .andExpect(jsonPath("$.supportSafe").value(true));
    }

    @Test
    void migrationControlPlaneStillRequiresWorkspaceScope() throws Exception {
        mockMvc.perform(post("/api/migration/dry-runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRunPayload()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    private String dryRunPayload() {
        return """
                {
                  "sourceProvider":"slack",
                  "inventory":{
                    "workspaces":1,
                    "channels":2,
                    "users":5,
                    "files":13,
                    "messages":200,
                    "scopes":["channels:read","users:read","files:read"]
                  }
                }
                """;
    }

    private String applyGatePayload() {
        return """
                {
                  "runId":"migration-chat-001",
                  "domainKey":"chat",
                  "requestedLifecycle":"approved",
                  "objectCounts":{"Conversation":2},
                  "contentHashes":["sha256:1111111111111111111111111111111111111111111111111111111111111111"],
                  "auditRefs":["audit:migration.dry_run:001"],
                  "identityMappingComplete":true,
                  "auditSinkAvailable":true,
                  "adminApproved":true
                }
                """;
    }

    private MigrationDryRunResponse dryRunResponse() {
        return new MigrationDryRunResponse(
                "migration-chat-001",
                "completed",
                "dry-run",
                "slack",
                new MigrationDryRunResponse.InventorySummary(1, 2, 5, 13, 200),
                new MigrationDryRunResponse.MappingProposal(2, 5, 0, List.of()),
                List.of(),
                List.of(),
                new MigrationDryRunResponse.UnmappableContentReport(0, List.of()),
                new MigrationDryRunResponse.ConsentRequirementReport(List.of(), List.of(), false),
                new MigrationDryRunResponse.RateLimitBudgetEstimate(1, 2, List.of()),
                List.of(),
                true,
                true,
                true,
                "/api/migration/dry-runs/migration-chat-001/report");
    }
}
