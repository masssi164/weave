package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.MatrixChatProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.service.LocalDependencyReadinessService;
import com.massimotter.weave.backend.service.PersistenceHealthProbe;
import com.massimotter.weave.backend.service.PlatformContractService;
import com.massimotter.weave.backend.service.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(
        controllers = {PlatformController.class, HealthController.class},
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        LocalDependencyReadinessService.class,
        PlatformContractService.class,
        WorkspaceCapabilityService.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties({
        MatrixChatProperties.class,
        PlatformContractProperties.class,
        WeaveSecurityProperties.class,
        WorkspaceCapabilityProperties.class,
        OAuth2ResourceServerProperties.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "weave.workspace.chat.dependency-url=https://matrix.weave.test",
        "weave.workspace.files.dependency-url=https://files.weave.test",
        "weave.workspace.calendar.enabled=true"
})
class PlatformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ProviderCapabilityHealthService providerCapabilityHealthService;

    @MockBean
    private PersistenceHealthProbe persistenceHealth;

    @BeforeEach
    void persistenceIsReady() {
        org.mockito.Mockito.when(persistenceHealth.ready()).thenReturn(true);
    }

    @Test
    void exposesPublicPlatformConfig() throws Exception {
        mockMvc.perform(get("/api/platform/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.organizationOrigin").value("https://weave.test"))
                .andExpect(jsonPath("$.controlPlaneBaseUrl").value("https://api.weave.test/api"))
                .andExpect(jsonPath("$.oidc.issuer").value("https://auth.weave.test/realms/weave"))
                .andExpect(jsonPath("$.oidc.clientId").value("weave-app"))
                .andExpect(jsonPath("$.protocols.matrixClientServerBaseUrl").value("https://api.weave.test"))
                .andExpect(jsonPath("$.protocols.filesWebDavBaseUrl").value("https://api.weave.test/api/dav/files"))
                .andExpect(jsonPath("$.protocols.calendarCalDavBaseUrl").value("https://api.weave.test/api/caldav"))
                .andExpect(jsonPath("$.releasePosture").value("dogfood"))
                .andExpect(jsonPath("$.domains.length()").value(6))
                .andExpect(jsonPath("$.domains[?(@.domain == 'chat')].state").value("available"))
                .andExpect(jsonPath("$.domains[?(@.domain == 'boards')].state").value("not_configured"))
                .andExpect(jsonPath("$.recoveryActions").isEmpty())
                .andExpect(jsonPath("$.publicBaseUrl").doesNotExist())
                .andExpect(jsonPath("$.matrixHomeserverUrl").doesNotExist())
                .andExpect(jsonPath("$.nextcloudBaseUrl").doesNotExist())
                .andExpect(jsonPath("$.targets").doesNotExist())
                .andExpect(jsonPath("$.features").doesNotExist());
    }

    @Test
    void exposesPublicPlatformStatus() throws Exception {
        mockMvc.perform(get("/api/platform/status")
                        .header("X-Request-Id", "diag-test-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "diag-test-1"))
                .andExpect(jsonPath("$.requestId").value("diag-test-1"))
                .andExpect(jsonPath("$.backend.status").value("up"))
                .andExpect(jsonPath("$.backend.readiness").value("ready"))
                .andExpect(jsonPath("$.auth.status").value("up"))
                .andExpect(jsonPath("$.auth.readiness").value("ready"))
                .andExpect(jsonPath("$.matrix.status").value("up"))
                .andExpect(jsonPath("$.matrix.readiness").value("ready"))
                .andExpect(jsonPath("$.matrix.federationEnabled").value(false))
                .andExpect(jsonPath("$.matrix.e2eeEnabled").value(false))
                .andExpect(jsonPath("$.matrix.e2ee.status").value("not_validated"))
                .andExpect(jsonPath("$.matrix.e2ee.source").value("backend_runtime_flags_only"))
                .andExpect(jsonPath("$.matrix.e2ee.encryptedRoomsValidated").value(false))
                .andExpect(jsonPath("$.matrix.e2ee.deviceVerificationValidated").value(false))
                .andExpect(jsonPath("$.matrix.e2ee.keyBackupValidated").value(false))
                .andExpect(jsonPath("$.matrix.e2ee.lostDeviceRecoveryValidated").value(false))
                .andExpect(jsonPath("$.matrix.e2ee.multiDeviceValidated").value(false))
                .andExpect(jsonPath("$.matrix.e2ee.accessibilityReviewed").value(false))
                .andExpect(jsonPath("$.matrix.backendBoundary.serverReadableMessageContent").value(false))
                .andExpect(jsonPath("$.matrix.backendBoundary.metadataReadable[0]").value("room_id"))
                .andExpect(jsonPath("$.matrix.backendBoundary.agentParticipation")
                        .value("blocked_until_explicit_consent_audit_and_matrix_device_trust_are_implemented"))
                .andExpect(jsonPath("$.matrix.backendBoundary.connectorWritePolicy")
                        .value("fail_closed_until_audit_consent_and_matrix_e2ee_client_identity_are_implemented"))
                .andExpect(jsonPath("$.files.status").value("up"))
                .andExpect(jsonPath("$.calendar.status").value("up"))
                .andExpect(jsonPath("$.nextcloud.status").value("up"))
                .andExpect(jsonPath("$.checks[?(@.key == 'auth')].readiness").value("ready"))
                .andExpect(jsonPath("$.checks[?(@.key == 'calendar')].status").value("up"))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    @Test
    void exposesPublicHealthEndpoints() throws Exception {
        mockMvc.perform(get("/api/health/live")
                        .header("X-Request-Id", "live-test-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "live-test-1"))
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.requestId").value("live-test-1"))
                .andExpect(jsonPath("$.checks[0].key").value("backend"));

        mockMvc.perform(get("/api/health/ready")
                        .header("X-Request-Id", "ready-test-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "ready-test-1"))
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.requestId").value("ready-test-1"))
                .andExpect(jsonPath("$.checks[?(@.key == 'auth')].readiness").value("ready"))
                .andExpect(jsonPath("$.checks[?(@.key == 'matrix')]").isEmpty())
                .andExpect(jsonPath("$.checks[?(@.key == 'files')]").isEmpty())
                .andExpect(jsonPath("$.checks[?(@.key == 'persistence')].readiness").value("ready"))
                .andExpect(jsonPath("$.actions").isEmpty());

        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk());
        verifyNoInteractions(providerCapabilityHealthService);
    }
}
