package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class McpExchangedTokenPolicyTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String API_RESOURCE = "https://api.weave.test/api";
    private static final String EDGE = "weave-mcp-server";

    private final McpExchangedTokenPolicy policy = new McpExchangedTokenPolicy(API_RESOURCE, EDGE);

    @Test
    void resolvesOnlyTheDownscopedEdgeTokenShape() {
        var token = policy.resolve(token(Map.of()));

        assertThat(token.issuer()).isEqualTo(ISSUER);
        assertThat(token.subject()).isEqualTo("service-account-cell-subject");
        assertThat(token.edgeClientId()).isEqualTo(EDGE);
        assertThat(token.scopes()).containsExactly("calendar.read");
    }

    @Test
    void rejectsHumanRolesMcpScopesAndMalformedRoleClaims() {
        assertDenied(
                token(Map.of("realm_access", Map.of("roles", List.of("member")))),
                "token-realm-roles");
        assertDenied(
                token(Map.of("resource_access", Map.of("weave-server", Map.of("roles", List.of("member"))))),
                "token-client-roles");
        assertDenied(token(Map.of("realm_access", Map.of("roles", "member"))), "token-realm-roles");
        assertDenied(token(Map.of("scope", "calendar.read mcp.tools")), "token-scope");
    }

    @Test
    void rejectsWrongAudienceAuthorizedPartyAndTokenType() {
        assertDenied(
                token(Map.of("aud", List.of(API_RESOURCE, "weave-mcp-server"))),
                "token-audience");
        assertDenied(token(Map.of("azp", "weave-app")), "token-requester");
        assertDenied(token(Map.of("typ", "JWT")), "token-type");
    }

    private void assertDenied(Jwt jwt, String reasonCode) {
        assertThatThrownBy(() -> policy.resolve(jwt))
                .isInstanceOfSatisfying(
                        McpWorkloadAuthorizationException.class,
                        failure -> assertThat(failure.reasonCode()).isEqualTo(reasonCode))
                .hasMessageNotContaining(jwt.getTokenValue());
    }

    private static Jwt token(Map<String, Object> overrides) {
        Instant now = Instant.now().minusSeconds(1);
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("azp", EDGE);
        claims.put("scope", "calendar.read");
        claims.put("realm_access", Map.of());
        claims.put("resource_access", Map.of());
        claims.putAll(overrides);
        Object audience = claims.remove("aud");
        Object type = claims.remove("typ");
        var builder = Jwt.withTokenValue("exchanged-secret-token")
                .header("alg", "RS256")
                .header("typ", type == null ? "at+jwt" : type)
                .issuer(ISSUER)
                .subject("service-account-cell-subject")
                .audience(audience == null ? List.of(API_RESOURCE) : castAudience(audience))
                .jti("exchange-jti")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(45));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> castAudience(Object value) {
        return (List<String>) value;
    }
}
