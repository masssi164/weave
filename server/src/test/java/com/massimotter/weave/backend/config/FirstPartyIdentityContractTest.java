package com.massimotter.weave.backend.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.local/realms/weave",
        "weave.security.required-audience=weave-app",
        "weave.security.client-id=weave-app"
})
@AutoConfigureMockMvc
class FirstPartyIdentityContractTest {

    private static final String REQUIRED_AUDIENCE = "weave-app";
    private static final String FIRST_PARTY_CLIENT_ID = "weave-app";
    private static final String WORKSPACE_CAPABILITIES_PATH = "/api/workspace/capabilities";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void configureJwtDecoder() {
        given(jwtDecoder.decode(anyString())).willAnswer(invocation -> decode(invocation.getArgument(0)));
    }

    @Test
    void acceptsTokenWithFirstPartyContractAndWorkspaceScope() throws Exception {
        mockMvc.perform(get(WORKSPACE_CAPABILITIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-contract"))
                .andExpect(status().isOk());
    }

    @Test
    void keepsVersionedWorkspaceCapabilitiesPathAsCompatibilityAlias() throws Exception {
        mockMvc.perform(get("/api/v1/workspace/capabilities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-contract"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-audience", "missing-audience"})
    void rejectsTokensWithoutRequiredAudience(String tokenValue) throws Exception {
        mockMvc.perform(get(WORKSPACE_CAPABILITIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").value(
                        "Bearer authentication is required and must satisfy the first-party Weave token contract."))
                .andExpect(jsonPath("$.details.status").value(401))
                .andExpect(jsonPath("$.details.path").value(endsWith(WORKSPACE_CAPABILITIES_PATH)))
                .andExpect(jsonPath("$.requestId").value(notNullValue()));
    }

    @Test
    void rejectsTokenWithoutWorkspaceScope() throws Exception {
        mockMvc.perform(get(WORKSPACE_CAPABILITIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer missing-scope"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.message").value(
                        "The bearer token is authenticated but missing the required weave:workspace scope."))
                .andExpect(jsonPath("$.details.status").value(403))
                .andExpect(jsonPath("$.details.path").value(endsWith(WORKSPACE_CAPABILITIES_PATH)))
                .andExpect(jsonPath("$.requestId").value(notNullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "missing-authorized-party",
            "wrong-azp",
            "wrong-client-id",
            "conflicting-authorized-party"
    })
    void rejectsTokensWithoutRequiredAuthorizedParty(String tokenValue) throws Exception {
        mockMvc.perform(get(WORKSPACE_CAPABILITIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsClientIdClaimWhenAzpIsAbsent() throws Exception {
        mockMvc.perform(get(WORKSPACE_CAPABILITIES_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-id-only"))
                .andExpect(status().isOk());
    }

    @Test
    void normalizesIssuedForFromClientIdWhenAzpIsAbsent() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-id-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedFor").value(FIRST_PARTY_CLIENT_ID))
                .andExpect(jsonPath("$.userId").value("user-123"));
    }

    @Test
    void acceptsValidFullFirstPartyToken() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-full-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.displayName").value("Alice Example"))
                .andExpect(jsonPath("$.issuedFor").value(FIRST_PARTY_CLIENT_ID))
                .andExpect(jsonPath("$.audience[0]").value(REQUIRED_AUDIENCE))
                .andExpect(jsonPath("$.realmRoles[0]").value("member"));
    }

    private Jwt decode(String tokenValue) {
        Jwt jwt = switch (tokenValue) {
            case "valid-contract" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "weave:workspace",
                    Map.of("azp", FIRST_PARTY_CLIENT_ID));
            case "client-id-only" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "weave:workspace",
                    Map.of("client_id", FIRST_PARTY_CLIENT_ID));
            case "wrong-audience" -> jwt(tokenValue, List.of("other-api"), "weave:workspace",
                    Map.of("azp", FIRST_PARTY_CLIENT_ID));
            case "missing-audience" -> jwt(tokenValue, null, "weave:workspace",
                    Map.of("azp", FIRST_PARTY_CLIENT_ID));
            case "missing-scope" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "openid profile",
                    Map.of("azp", FIRST_PARTY_CLIENT_ID));
            case "missing-authorized-party" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "weave:workspace", null);
            case "wrong-azp" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "weave:workspace",
                    Map.of("azp", "other-client"));
            case "wrong-client-id" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "weave:workspace",
                    Map.of("client_id", "other-client"));
            case "conflicting-authorized-party" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "weave:workspace",
                    Map.of("azp", FIRST_PARTY_CLIENT_ID, "client_id", "other-client"));
            case "valid-full-token" -> jwt(tokenValue, List.of(REQUIRED_AUDIENCE), "openid profile weave:workspace",
                    Map.of(
                            "preferred_username", "alice",
                            "name", "Alice Example",
                            "email", "alice@example.com",
                            "azp", FIRST_PARTY_CLIENT_ID,
                            "client_id", FIRST_PARTY_CLIENT_ID,
                            "realm_access", Map.of("roles", List.of("member"))));
            default -> throw new JwtException("Unknown test token.");
        };

        OAuth2TokenValidator<Jwt> validator = new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtDecoderConfig.requiredAudienceValidator(REQUIRED_AUDIENCE),
                JwtDecoderConfig.requiredAuthorizedPartyValidator(FIRST_PARTY_CLIENT_ID));
        OAuth2TokenValidatorResult validation = validator.validate(jwt);
        if (validation.hasErrors()) {
            throw new JwtValidationException("JWT does not satisfy the first-party contract.",
                    validation.getErrors());
        }

        return jwt;
    }

    private Jwt jwt(String tokenValue, List<String> audience, String scope, Map<String, Object> claims) {
        Instant now = Instant.parse("2026-04-18T12:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .issuer("https://auth.weave.local/realms/weave")
                .subject("user-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope);

        if (audience != null) {
            builder.audience(audience);
        }
        if (claims != null) {
            claims.forEach(builder::claim);
        }

        return builder.build();
    }
}
