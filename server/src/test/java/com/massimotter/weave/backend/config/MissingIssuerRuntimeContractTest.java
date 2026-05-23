package com.massimotter.weave.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MissingIssuerRuntimeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsWithoutIssuerAndFailsProtectedRoutesClosed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me")
                .header(AUTHORIZATION, "Bearer invalid-without-issuer"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.details.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Bearer authentication is required and must satisfy the first-party Weave token contract."));
    }

    @Test
    void exposesActionableDiagnosticsWhenAuthContractIsIncomplete() throws Exception {
        mockMvc.perform(get("/api/platform/status")
                        .header("X-Request-Id", "missing-auth-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "missing-auth-test"))
                .andExpect(jsonPath("$.requestId").value("missing-auth-test"))
                .andExpect(jsonPath("$.auth.status").value("blocked"))
                .andExpect(jsonPath("$.auth.readiness").value("blocked"))
                .andExpect(jsonPath("$.auth.message").value("Missing auth runtime inputs: WEAVE_OIDC_ISSUER_URI."))
                .andExpect(jsonPath("$.matrix.status").value("blocked"))
                .andExpect(jsonPath("$.files.status").value("blocked"))
                .andExpect(jsonPath("$.calendar.status").value("disabled"))
                .andExpect(jsonPath("$.actions[0]").value(
                        "Provide the missing auth runtime inputs for the backend: WEAVE_OIDC_ISSUER_URI."));

        mockMvc.perform(get("/api/health/ready")
                        .header("X-Request-Id", "missing-auth-ready-test"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Request-Id", "missing-auth-ready-test"))
                .andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.requestId").value("missing-auth-ready-test"))
                .andExpect(jsonPath("$.checks[?(@.key == 'auth')].readiness").value("blocked"))
                .andExpect(jsonPath("$.actions[0]").value(
                        "Provide the missing auth runtime inputs for the backend: WEAVE_OIDC_ISSUER_URI."));
    }
}
