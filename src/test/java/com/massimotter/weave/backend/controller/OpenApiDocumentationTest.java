package com.massimotter.weave.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.local/realms/weave"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void exposesOpenApiDescription() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
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
                .andExpect(jsonPath("$.paths['/api/profile'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/profile/sync-status']").exists())
                .andExpect(jsonPath("$.paths['/api/files']").exists())
                .andExpect(jsonPath("$.paths['/api/files/upload']").exists())
                .andExpect(jsonPath("$.paths['/api/files/folders']").exists())
                .andExpect(jsonPath("$.paths['/api/files/{id}/download']").exists())
                .andExpect(jsonPath("$.paths['/api/files/{id}']").exists())
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
                .andExpect(jsonPath("$.paths['/api/connectors/boundary']").exists())
                .andExpect(jsonPath("$.paths['/api/connectors/manifest/validate']").exists())
                .andExpect(jsonPath("$.components.schemas.BoardsPreviewResponse.properties.syncMetadata").exists())
                .andExpect(jsonPath("$.components.schemas.BoardsSyncMetadataResponse.properties.provider.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.BoardsSyncMetadataResponse.properties.nextCursors.type").value("object"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.code.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.requestId.type").value("string"))
                .andExpect(jsonPath("$.components.responses.UnauthorizedError.description").value("Missing or invalid bearer token."))
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].type").value("http"));
    }
}
