package com.massimotter.weave.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.agentruntime.adapter.AgentRuntimeWorkloadTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.RuntimeProfileDeliveryService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfileJwkSet;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.config.AgentRuntimeProfileSecurityConfiguration;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AgentRuntimeProfileController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        AgentRuntimeProfileSecurityConfiguration.class,
        AgentRuntimeProfileControllerTest.PolicyConfiguration.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
@EnableConfigurationProperties({
        PlatformContractProperties.class,
        OAuth2ResourceServerProperties.class
})
@TestPropertySource(properties = {
        "weave.agent-runtime.workload-identity.enabled=true",
        "weave.agent-runtime.profile-signing.enabled=true",
        "weave.platform.api-base-url=https://api.weave.test/api"
})
class AgentRuntimeProfileControllerTest {
    private static final String RESOURCE = "https://api.weave.test/api/v1/agent-runtime";
    private static final String CLIENT = "weaver-cell-example";
    private static final String SUBJECT = "service-account-subject-1";
    private static final String HASH = "sha256:" + "a".repeat(64);
    private static final RuntimeWorkloadPrincipal PRINCIPAL = new RuntimeWorkloadPrincipal(
            "https://auth.weave.test/realms/weave", SUBJECT, CLIENT);
    private static final SignedRuntimeProfile PROFILE = new SignedRuntimeProfile(
            "header", "payload", "A".repeat(86), HASH,
            "rp_example", "cell:example", "key-1",
            Instant.parse("2026-07-20T09:59:30Z"), Instant.parse("2026-07-20T10:00:30Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RuntimeProfileDeliveryService profiles;

    @MockitoBean
    private RuntimeProfileTrustBundlePublisher trustBundle;

    @Test
    void publishesCurrentPublicJwksWithoutAuthenticationAndPreventsStaleTrustCaching() throws Exception {
        RuntimeProfileJwkSet jwks = new RuntimeProfileJwkSet(List.of(
                new RuntimeProfileJwkSet.Jwk(
                        "OKP", "Ed25519", "A".repeat(43), "sig", "EdDSA", "key-1")));
        given(trustBundle.publish(any(Instant.class))).willReturn(Optional.of(jwks));

        mockMvc.perform(get("/api/v1/agent-runtime/trust/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/jwk-set+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.keys[0].kty").value("OKP"))
                .andExpect(jsonPath("$.keys[0].crv").value("Ed25519"))
                .andExpect(jsonPath("$.keys[0].kid").value("key-1"));
    }

    @Test
    void returns404WhenNoTrustKeyIsPublished() throws Exception {
        given(trustBundle.publish(any(Instant.class))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/agent-runtime/trust/jwks.json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("agent-runtime-trust-unavailable"));
    }

    @Test
    void returnsOnlyTheCellBoundFlattenedJwsAndNeverCachesIt() throws Exception {
        given(profiles.findCurrent(HASH, PRINCIPAL)).willReturn(Optional.of(PROFILE));

        mockMvc.perform(get("/api/v1/agent-runtime/runtime-profiles/{profileHash}", HASH)
                        .with(validWorkload()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.protected").value(PROFILE.protectedHeader()))
                .andExpect(jsonPath("$.payload").value(PROFILE.payload()))
                .andExpect(jsonPath("$.signature").value(PROFILE.signature()))
                .andExpect(jsonPath("$.profileHash").doesNotExist())
                .andExpect(jsonPath("$.profileId").doesNotExist())
                .andExpect(jsonPath("$.cellRef").doesNotExist())
                .andExpect(jsonPath("$.keyId").doesNotExist());
    }

    @Test
    void missingOrInsufficientBearerUsesTheWorkloadSpecificSecurityErrors() throws Exception {
        mockMvc.perform(get("/api/v1/agent-runtime/runtime-profiles/{profileHash}", HASH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("agent-runtime-workload-unauthorized"));

        mockMvc.perform(get("/api/v1/agent-runtime/runtime-profiles/{profileHash}", HASH)
                        .with(jwt().authorities()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent-runtime-workload-forbidden"));
    }

    @Test
    void memberClaimsAndCrossCellProfileLookupsRevealNoEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/agent-runtime/runtime-profiles/{profileHash}", HASH)
                        .with(jwt()
                                .jwt(token -> token
                                        .issuer("https://auth.weave.test/realms/weave")
                                        .subject("member-subject")
                                        .issuedAt(Instant.parse("2026-07-20T10:00:00Z"))
                                        .expiresAt(Instant.parse("2026-07-20T10:01:00Z"))
                                        .audience(List.of(RESOURCE))
                                        .claim("jti", "member-jti")
                                        .claim("client_id", "weave-app")
                                        .claim("azp", "weave-app")
                                        .claim("scope", AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE)
                                        .claim("realm_access", Map.of("roles", List.of("member"))))
                                .authorities(new SimpleGrantedAuthority(
                                        AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_AUTHORITY))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent-runtime-workload-forbidden"));

        given(profiles.findCurrent(eq(HASH), eq(PRINCIPAL))).willReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/agent-runtime/runtime-profiles/{profileHash}", HASH)
                        .with(validWorkload()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("runtime-profile-unavailable"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            validWorkload() {
        return jwt()
                .jwt(token -> token
                        .issuer("https://auth.weave.test/realms/weave")
                        .subject(SUBJECT)
                        .issuedAt(Instant.parse("2026-07-20T10:00:00Z"))
                        .expiresAt(Instant.parse("2026-07-20T10:01:00Z"))
                        .audience(List.of(RESOURCE))
                        .claim("jti", "workload-jti")
                        .claim("client_id", CLIENT)
                        .claim("azp", CLIENT)
                        .claim("scope", AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE)
                        .claim("realm_access", Map.of(
                                "roles", List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE))))
                .authorities(new SimpleGrantedAuthority(
                        AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_AUTHORITY));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PolicyConfiguration {
        @Bean
        AgentRuntimeWorkloadTokenPolicy agentRuntimeWorkloadTokenPolicy(PlatformContractProperties platform) {
            return new AgentRuntimeWorkloadTokenPolicy(platform.agentRuntimeControlResource());
        }
    }
}
