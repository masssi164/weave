package com.massimotter.weave.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/certs",
        "weave.oidc.token-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/token",
        "weave.oidc.mcp-client-secret=test-only-secret"
})
@AutoConfigureMockMvc
class SpringAiMcpTransportTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void decodableOidcTokens() {
        org.mockito.Mockito.when(jwtDecoder.decode(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> token(invocation.getArgument(0)));
    }

    private Jwt token(String tokenValue) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject(tokenValue))
                .audience(List.of("weave-mcp-server"))
                .claim("azp", authorizedParty(tokenValue))
                .claim("scope", "weave:mcp")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private String subject(String tokenValue) {
        return tokenValue.equals("member") ? "member@example.invalid" : "service-account-" + tokenValue;
    }

    private String authorizedParty(String tokenValue) {
        return tokenValue.equals("member") ? "weave-app" : tokenValue;
    }

    @Test
    void oidcIsTheGatekeeperForSpringAiMcpTransport() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(initializeRequest()))
                .andExpect(status().isUnauthorized());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "member", "weave-mcp-server", "generic-service-account", "weaver-cell-test"
    })
    void mcpRemainsDarkForHumansAndUnboundWorkloadsUntilArcBindingExists(String tokenValue) throws Exception {
        mvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue)
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(initializeRequest()))
                .andExpect(status().isForbidden());
    }

    private String initializeRequest() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
                "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"elicitation\":{\"form\":{}}}," +
                "\"clientInfo\":{\"name\":\"weave-test\",\"version\":\"1.0\"}}}";
    }
}
