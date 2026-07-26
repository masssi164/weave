package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.service.files.NextcloudFilesAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
class ProviderHealthActuatorMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoSpyBean
    private NextcloudFilesAdapter nextcloudFilesAdapter;

    @Test
    void localRunnerCanReadProviderHealthMetricsWithoutTriggeringAProbe() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItems(
                        "weave.provider.health.status",
                        "weave.provider.health.probe.latency",
                        "weave.provider.health.consecutive.failures",
                        "weave.provider.health.backoff.until.epoch.seconds",
                        "weave.provider.health.cached.age.seconds",
                        "weave.provider.health.readiness.transitions")));

        mockMvc.perform(get("/actuator/metrics/weave.provider.health.status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("weave.provider.health.status"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'capability')].values",
                        contains(hasItems("files", "calendar", "chat"))));

        mockMvc.perform(get("/actuator/metrics/weave.provider.health.probe.latency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'capability')].values",
                        contains(hasItems("files", "calendar"))))
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'status')].values",
                        contains(hasItems("available", "degraded", "unavailable"))));

        mockMvc.perform(get("/api/admin/provider-capability-health"))
                .andExpect(status().isUnauthorized());

        verify(nextcloudFilesAdapter, never()).healthProbe();
    }
}
