package com.massimotter.weave.backend.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "weave.identity.invitations.keycloak.enabled=false",
        "weave.platform.api-base-url=https://api.weave.test/api",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave"
})
@AutoConfigureMockMvc
class AdminConsoleWebConfigurationTest {

    private static final String FIXTURE_TEXT = "Admin Console test fixture";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesScopedAssetsAndClientSideRoutesWithStaticSecurityHeaders() throws Exception {
        mockMvc.perform(get("/admin-console/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(FIXTURE_TEXT)))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString(
                        "connect-src 'self' https://auth.weave.test")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        mockMvc.perform(get("/admin-console/settings/members"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(FIXTURE_TEXT)))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));

        mockMvc.perform(get("/admin-console/assets/admin-console-test.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("adminConsoleFixture")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void neverUsesTheSpaFallbackForControlProtocolManagementOrSecurityRoutes() throws Exception {
        for (String path : new String[] {
                "/",
                "/future-server-route",
                "/api/not-a-route",
                "/mcp/not-a-route",
                "/caldav/not-a-route",
                "/webdav/not-a-route",
                "/dav/not-a-route",
                "/actuator/not-a-route",
                "/oauth2/not-a-route",
                "/login/not-a-route",
                "/.well-known/not-a-route",
                "/v3/not-a-route",
                "/_matrix/not-a-route"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString(FIXTURE_TEXT))));
        }
    }

    @Test
    void existingPublicApiAndActuatorRoutesKeepTheirNativeHandlers() throws Exception {
        mockMvc.perform(get("/api/platform/config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString(FIXTURE_TEXT))));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")))
                .andExpect(content().string(not(containsString(FIXTURE_TEXT))));
    }

    @Test
    void missingStaticFilesReturnNotFoundInsteadOfTheSpaIndex() throws Exception {
        mockMvc.perform(get("/admin-console/assets/missing.js"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(FIXTURE_TEXT))));

        mockMvc.perform(get("/admin-console/missing.css"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(FIXTURE_TEXT))));
    }
}
