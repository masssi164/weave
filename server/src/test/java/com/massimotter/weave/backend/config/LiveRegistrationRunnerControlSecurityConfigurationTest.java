package com.massimotter.weave.backend.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.controller.RunnerLiveRegistrationController;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityDisposition;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityResult;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicationDisposition;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicationResult;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.http.RunnerLiveRegistrationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
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
        controllers = RunnerLiveRegistrationController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        RunnerControlSecurityConfiguration.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
class LiveRegistrationRunnerControlSecurityConfigurationTest {

    private static final byte[] CERTIFICATE_BYTES =
            "runner-live-registration-certificate".getBytes(StandardCharsets.UTF_8);
    private static final String FINGERPRINT = fingerprint(CERTIFICATE_BYTES);
    private static final String DIGEST =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BUNDLE = """
            {
              "schemaVersion": "weave.runner.public-capability-bundle/v1",
              "bundleId": "internal.cmdb",
              "bundleVersion": "1.0.0",
              "bundleDigest": "%s",
              "capabilities": [{
                "id": "internal.cmdb.lookup",
                "version": "1.0.0",
                "title": "Internal CMDB lookup",
                "description": "Returns one bounded asset record.",
                "effect": "READ_ONLY",
                "inputSchema": {"type":"object"},
                "inputSchemaDigest": "%s",
                "outputSchema": {"type":"object"},
                "outputSchemaDigest": "%s",
                "timeoutSeconds": 60,
                "maxOutputBytes": 4096,
                "artifactTypes": ["cmdb-report"],
                "contractDigest": "%s"
              }]
            }
            """.formatted(DIGEST, DIGEST, DIGEST, DIGEST);
    private static final String HEARTBEAT = """
            {
              "runnerId": "runner_live_registration_01",
              "runnerVersion": "1.2.3",
              "bundleDigest": "%s",
              "runningTasks": 1,
              "capacity": 4,
              "observedAt": "2026-08-28T22:00:00Z"
            }
            """.formatted(DIGEST);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RunnerWorkloadIdentityDirectory identities;

    @MockitoBean
    private RunnerLiveRegistrationService service;

    private X509Certificate certificate;
    private RunnerWorkloadIdentity identity;

    @BeforeEach
    void setUp() throws Exception {
        certificate = org.mockito.Mockito.mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(CERTIFICATE_BYTES);
        identity = new RunnerWorkloadIdentity(
                new RunnerId("runner_live_registration_01"),
                "org:live-registration",
                FINGERPRINT,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        when(identities.resolveActive(eq(FINGERPRINT), any(Instant.class)))
                .thenReturn(Optional.of(identity));
        when(service.publish(eq(identity), any()))
                .thenReturn(new PublicationResult(7, PublicationDisposition.CREATED, 1, 1));
        when(service.heartbeat(eq(identity), any()))
                .thenReturn(new AvailabilityResult(AvailabilityDisposition.CREATED, 1, 3));
    }

    @Test
    void bearerAndSpoofedHeadersCannotPublishCapabilities() throws Exception {
        mockMvc.perform(put(RunnerLiveRegistrationController.BUNDLE_PATH)
                        .header("Authorization", "Bearer spoofed")
                        .header("X-Weave-Runner-Id", "runner_live_registration_01")
                        .header("Idempotency-Key", "publish-capability-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BUNDLE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("runner-certificate-required"));

        verify(service, never()).publish(any(), any());
    }

    @Test
    void activeCertificatePublishesSanitizedBundle() throws Exception {
        mockMvc.perform(put(RunnerLiveRegistrationController.BUNDLE_PATH)
                        .requestAttr(
                                "jakarta.servlet.request.X509Certificate",
                                new X509Certificate[] {certificate})
                        .header("Idempotency-Key", "publish-capability-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BUNDLE))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Weave-Catalog-Revision", "7"));

        verify(service).publish(eq(identity), any());
    }

    @Test
    void activeCertificateUpdatesRunnerAvailability() throws Exception {
        mockMvc.perform(post(RunnerLiveRegistrationController.HEARTBEAT_PATH)
                        .requestAttr(
                                "jakarta.servlet.request.X509Certificate",
                                new X509Certificate[] {certificate})
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HEARTBEAT))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Weave-Available-Slots", "3"));

        verify(service).heartbeat(eq(identity), any());
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
