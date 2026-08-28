package com.massimotter.weave.backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.controller.RunnerTaskClaimController;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimHttpResponse;
import com.massimotter.weave.backend.runner.application.RunnerTaskClaimService;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = RunnerTaskClaimController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        RunnerControlSecurityConfiguration.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
class RunnerControlSecurityConfigurationTest {

    private static final String PATH = "/runner/v1/tasks:claim";
    private static final byte[] CERTIFICATE_BYTES =
            "runner-security-certificate".getBytes(StandardCharsets.UTF_8);
    private static final String FINGERPRINT = fingerprint(CERTIFICATE_BYTES);
    private static final String REQUEST = """
            {
              "runnerId": "runner_security_01",
              "bundleDigest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "availableSlots": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunnerWorkloadIdentityDirectory identities;

    @MockitoBean
    private RunnerTaskClaimService claimService;

    private X509Certificate certificate;
    private RunnerWorkloadIdentity identity;

    @BeforeEach
    void setUp() throws Exception {
        certificate = org.mockito.Mockito.mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(CERTIFICATE_BYTES);
        identity = new RunnerWorkloadIdentity(
                new RunnerId("runner_security_01"),
                "org:security-test",
                FINGERPRINT,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        when(identities.resolveActive(eq(FINGERPRINT), any(Instant.class)))
                .thenReturn(Optional.of(identity));
        when(claimService.claim(any(), anyList(), any()))
                .thenReturn(new ClaimHttpResponse<>(
                        204,
                        Map.of(
                                "Cache-Control", "no-store",
                                "Preference-Applied", "wait=0",
                                "Retry-After", "1"),
                        Optional.empty()));
    }

    @Test
    void bearerAndSpoofedIdentityHeadersCannotEnterRunnerControlPlane() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer spoofed")
                        .header("X-Weave-Runner-Id", "runner_security_01")
                        .header("X-Weave-Runner-Certificate-Fingerprint", FINGERPRINT)
                        .header("Prefer", "wait=0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("runner-certificate-required"));

        verify(claimService, never()).claim(any(), anyList(), any());
    }

    @Test
    void unknownCertificateFailsBeforeTaskDisclosure() throws Exception {
        when(identities.resolveActive(eq(FINGERPRINT), any(Instant.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(PATH)
                        .requestAttr(
                                "jakarta.servlet.request.X509Certificate",
                                new X509Certificate[] {certificate})
                        .header("Prefer", "wait=0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("runner-certificate-required"));

        verify(claimService, never()).claim(any(), anyList(), any());
    }

    @Test
    void activeCertificateReachesCanonicalClaimBoundary() throws Exception {
        mockMvc.perform(post(PATH)
                        .requestAttr(
                                "jakarta.servlet.request.X509Certificate",
                                new X509Certificate[] {certificate})
                        .header("Prefer", "wait=0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Preference-Applied", "wait=0"))
                .andExpect(header().string("Retry-After", "1"));

        verify(claimService).claim(eq(identity), eq(java.util.List.of("wait=0")), any());
    }

    private static String fingerprint(byte[] encoded) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
