package com.massimotter.weave.backend.controller;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void exposesOpenApiDescription() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Weave Backend API"))
                .andExpect(jsonPath("$.paths['/api/me']").exists())
                .andExpect(jsonPath("$.paths['/api/health/live']").exists())
                .andExpect(jsonPath("$.paths['/api/health/ready']").exists())
                .andExpect(jsonPath("$.paths['/api/platform/config']").exists())
                .andExpect(jsonPath("$.paths['/api/platform/status']").exists())
                .andExpect(jsonPath("$.paths['/api/onboarding/status']").exists())
                .andExpect(jsonPath("$.paths['/api/profile']").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].get").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].get.operationId").value("getProductProfile"))
                .andExpect(jsonPath("$.paths['/api/profile'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].patch.operationId").value("updateProductProfile"))
                .andExpect(jsonPath("$.paths['/api/profile/sync-status']").exists())
                .andExpect(jsonPath("$.paths['/api/profile/sync-status'].get.operationId").value("getProductProfileSyncStatus"))
                .andExpect(jsonPath("$.paths['/api/files']").exists())
                .andExpect(jsonPath("$.paths['/api/files'].get.operationId").value("listFiles"))
                .andExpect(jsonPath("$.paths['/api/files/upload']").exists())
                .andExpect(jsonPath("$.paths['/api/files/upload'].post.operationId").value("uploadFile"))
                .andExpect(jsonPath("$.paths['/api/files/folders']").exists())
                .andExpect(jsonPath("$.paths['/api/files/folders'].post.operationId").value("createFilesFolder"))
                .andExpect(jsonPath("$.paths['/api/files/readiness']").exists())
                .andExpect(jsonPath("$.paths['/api/files/readiness'].get.operationId").value("getFilesReadiness"))
                .andExpect(jsonPath("$.paths['/api/files/readiness'].get.responses['200'].content['*/*'].schema['$ref']")
                        .value("#/components/schemas/WorkspaceCapabilityStatusResponse"))
                .andExpect(jsonPath("$.paths['/api/files/{id}/download']").exists())
                .andExpect(jsonPath("$.paths['/api/files/{id}/download'].get.operationId").value("downloadFile"))
                .andExpect(jsonPath("$.paths['/api/files/{id}/download'].get.responses['200'].description").value("Downloaded file bytes."))
                .andExpect(jsonPath("$.paths['/api/files/{id}/download'].get.responses['200'].content['application/octet-stream'].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/files/{id}/download'].get.responses['200'].content['application/octet-stream'].schema.format")
                        .value("binary"))
                .andExpect(jsonPath("$.paths['/api/files/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/files/{id}'].delete.operationId").value("deleteFile"))
                .andExpect(jsonPath("$.paths['/api/files/{id}'].delete.responses['204'].description").value("File or folder deleted."))
                .andExpect(jsonPath("$.paths['/api/calendar/events']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/access-policy']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup/credentials']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup/credentials/{credentialId}']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/client-setup/apple.mobileconfig']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/events/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/calendar/events/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/workspace/capabilities']").exists())
                .andExpect(jsonPath("$.paths['/api/workspace/release-readiness']").exists())
                .andExpect(jsonPath("$.paths['/api/providers/status']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspace/capabilities']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspace/release-readiness']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/status']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/status']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/oauth/callback']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/events']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/slack/messages']").exists())
                .andExpect(jsonPath("$.paths['/api/interop/teams/contract']").exists())
                .andExpect(jsonPath("$.paths['/api/guest/access-contract']").exists())
                .andExpect(jsonPath("$.paths['/api/guest/invitations']").exists())
                .andExpect(jsonPath("$.paths['/api/migration/dry-runs']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/providers/replacements/dry-run']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/control-plane'].get.operationId").value("getAdminControlPlane"))
                .andExpect(jsonPath("$.paths['/api/admin/policies/capability-whitelist'].get.operationId").value("getCapabilityWhitelist"))
                .andExpect(jsonPath("$.paths['/api/admin/policies/capability-whitelist'].patch.operationId").value("updateCapabilityWhitelist"))
                .andExpect(jsonPath("$.paths['/api/admin/providers/readiness-tests'].post.operationId").value("testProviderReadiness"))
                .andExpect(jsonPath("$.paths['/api/admin/providers/replacements/dry-run'].post.operationId").value("dryRunProviderReplacement"))
                .andExpect(jsonPath("$.paths['/api/chat/conversations'].get.operationId").value("listChatConversations"))
                .andExpect(jsonPath("$.paths['/api/chat/conversations/{conversationId}/messages'].get.operationId").value("listChatMessages"))
                .andExpect(jsonPath("$.paths['/api/chat/conversations/{conversationId}/messages'].post.operationId").value("sendChatMessage"))
                .andExpect(jsonPath("$.paths['/api/chat/conversations/{conversationId}/weaver/scout/summaries'].post.operationId").value("createWeaverScoutSummary"))
                .andExpect(jsonPath("$.paths['/api/admin/chat/readiness'].get.operationId").value("getAdminChatReadiness"))
                .andExpect(jsonPath("$.paths['/api/admin/chat/provider-replacements/dry-run'].post.operationId").value("dryRunChatProviderReplacement"))
                .andExpect(jsonPath("$.paths['/api/connectors/boundary']").exists())
                .andExpect(jsonPath("$.paths['/api/connectors/manifest/validate']").exists())
                .andExpect(jsonPath("$.components.schemas.BoardsWorkspaceResponse.properties.syncMetadata").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderRegistryResponse.properties.providers").exists())
                .andExpect(jsonPath("$.components.schemas.ProviderStatusResponse.properties.providerKey.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ProviderStatusResponse.properties.diagnostics.type").value("object"))
                .andExpect(jsonPath("$.components.schemas.BoardsSyncMetadataResponse.properties.provider.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.BoardsSyncMetadataResponse.properties.nextCursors.type").value("object"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.requestId.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.supportRef.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.memberImpact.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.required", hasItems(
                        "code",
                        "message",
                        "details",
                        "requestId",
                        "supportRef")))
                .andExpect(jsonPath("$.components.responses.UnauthorizedError.description").value("Missing or invalid bearer token."))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"))
                .andReturn();

        String exportPath = System.getProperty("weave.openapi.export.path");
        if (exportPath != null && !exportPath.isBlank()) {
            Path path = Path.of(exportPath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, result.getResponse().getContentAsString());
        }
    }
}
