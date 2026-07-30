package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import com.massimotter.weave.backend.persistence.jpa.readiness.JpaPersistenceReadinessProbe;
import com.massimotter.weave.backend.service.LocalDependencyReadinessService;
import com.massimotter.weave.backend.service.PlatformContractService;
import com.massimotter.weave.backend.service.ProviderCapabilityHealthService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiErrorResponseWriter.class, LocalDependencyReadinessService.class})
class HealthControllerPersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformContractService platformContractService;

    @MockitoBean
    private JpaPersistenceReadinessProbe persistenceReadinessProbe;

    @MockitoBean
    private ProviderCapabilityHealthService providerCapabilityHealthService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(platformContractService.status(any())).thenAnswer(invocation -> readyStatus(invocation.getArgument(0)));
    }

    @Test
    void reportsReadyWhenConfiguredJpaPersistenceIsReachable() throws Exception {
        when(persistenceReadinessProbe.isReady()).thenReturn(true);

        mockMvc.perform(get("/api/health/ready").header("X-Request-Id", "jpa-ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.requestId").value("jpa-ready"))
                .andExpect(jsonPath("$.checks[?(@.key == 'persistence')].status").value("up"))
                .andExpect(jsonPath("$.checks[?(@.key == 'persistence')].readiness").value("ready"))
                .andExpect(jsonPath("$.actions").isEmpty());

        verifyNoInteractions(providerCapabilityHealthService);
    }

    @Test
    void reportsServiceUnavailableWithSupportSafePersistenceFailure() throws Exception {
        when(persistenceReadinessProbe.isReady())
                .thenThrow(new IllegalStateException(
                        "postgresql://db.internal/weave?password=do-not-expose"));

        mockMvc.perform(get("/api/health/ready").header("X-Request-Id", "jpa-failed"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.requestId").value("jpa-failed"))
                .andExpect(jsonPath("$.checks[?(@.key == 'persistence')].status").value("blocked"))
                .andExpect(jsonPath("$.checks[?(@.key == 'persistence')].readiness").value("blocked"))
                .andExpect(jsonPath("$.checks[?(@.key == 'persistence')].message")
                        .value("Configured persistence is unavailable."))
                .andExpect(content().string(not(containsString("do-not-expose"))))
                .andExpect(content().string(not(containsString("db.internal"))));

        verifyNoInteractions(providerCapabilityHealthService);
    }

    @Test
    void livenessDoesNotCheckPersistenceOrProviders() throws Exception {
        mockMvc.perform(get("/api/health/live").header("X-Request-Id", "live-process-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"))
                .andExpect(jsonPath("$.requestId").value("live-process-only"))
                .andExpect(jsonPath("$.checks[0].key").value("backend"));

        verifyNoInteractions(
                platformContractService, persistenceReadinessProbe, providerCapabilityHealthService);
    }

    private PlatformStatusResponse readyStatus(String requestId) {
        PlatformStatusResponse.DiagnosticStatus ready = new PlatformStatusResponse.DiagnosticStatus(
                "up", "ready", "Ready.", null);
        List<PlatformStatusResponse.DiagnosticCheck> checks = List.of(
                new PlatformStatusResponse.DiagnosticCheck(
                        "backend", "Backend API", "up", "ready", "Ready.", null),
                new PlatformStatusResponse.DiagnosticCheck(
                        "auth", "Keycloak auth", "up", "ready", "Ready.", null));
        return new PlatformStatusResponse(
                requestId,
                ready,
                ready,
                null,
                null,
                null,
                null,
                checks,
                List.of());
    }
}
