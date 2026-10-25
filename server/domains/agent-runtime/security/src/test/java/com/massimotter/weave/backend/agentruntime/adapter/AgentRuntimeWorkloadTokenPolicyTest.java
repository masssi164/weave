package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeWorkloadTokenException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AgentRuntimeWorkloadTokenPolicyTest {
    private static final String RESOURCE = "https://api.weave.test/api/v1/agent-runtime";
    private static final String CLIENT = "weaver-cell-example";
    private static final String SUBJECT = "service-account-subject-1";
    private final AgentRuntimeWorkloadTokenPolicy policy = new AgentRuntimeWorkloadTokenPolicy(RESOURCE);

    @Test
    void resolvesOnlyTheExactCellWorkloadClaimShape() {
        RuntimeWorkloadPrincipal principal = policy.resolve(token(
                CLIENT,
                CLIENT,
                List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE),
                Map.of()));

        assertThat(principal).isEqualTo(new RuntimeWorkloadPrincipal(
                "https://auth.weave.test/realms/weave", SUBJECT, CLIENT));
    }

    @Test
    void acceptsOnlyTheBoundedKeycloakDefaultAndAccountRoleProjection() {
        RuntimeWorkloadPrincipal principal = policy.resolve(token(
                CLIENT,
                CLIENT,
                List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(
                        AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE,
                        "default-roles-weave",
                        "offline_access",
                        "uma_authorization"),
                Map.of("account", Map.of("roles", List.of(
                        "manage-account", "manage-account-links", "view-profile")))));

        assertThat(principal).isEqualTo(new RuntimeWorkloadPrincipal(
                "https://auth.weave.test/realms/weave", SUBJECT, CLIENT));
    }

    @Test
    void rejectsPublicGenericAndConflictingClients() {
        assertRejected(token(
                "weave-app", "weave-app", List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE), Map.of()),
                "invalid-client-binding");
        assertRejected(token(
                CLIENT, "another-client", List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE), Map.of()),
                "invalid-client-binding");

        Jwt missingClient = baseToken().claim("azp", CLIENT)
                .audience(List.of(RESOURCE)).claim("scope", AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE)
                .claim("realm_access", Map.of("roles", List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE)))
                .build();
        assertRejected(missingClient, "missing-client-id");
    }

    @Test
    void rejectsExtraAudienceScopeRealmRoleOrAnyClientRole() {
        assertRejected(token(
                CLIENT, CLIENT, List.of(RESOURCE, "https://api.weave.test/api"),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE), Map.of()),
                "invalid-audience");
        assertRejected(token(
                CLIENT, CLIENT, List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE + " agent-runtime.lifecycle.write",
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE), Map.of()),
                "invalid-scope");
        assertRejected(token(
                CLIENT, CLIENT, List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE, "member"), Map.of()),
                "invalid-workload-role");
        assertRejected(token(
                CLIENT, CLIENT, List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE),
                Map.of("weave-app", Map.of("roles", List.of("member")))),
                "invalid-client-roles");
        assertRejected(token(
                CLIENT, CLIENT, List.of(RESOURCE),
                AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE,
                List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE),
                Map.of("account", Map.of("roles", List.of("delete-account")))),
                "invalid-client-roles");
    }

    @Test
    void rejectsTokensWithoutRfc9068CorrelationClaims() {
        Jwt missingJti = Jwt.withTokenValue("workload-token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(SUBJECT)
                .issuedAt(Instant.parse("2026-07-20T10:00:00Z"))
                .expiresAt(Instant.parse("2026-07-20T10:01:00Z"))
                .audience(List.of(RESOURCE))
                .claim("client_id", CLIENT)
                .claim("azp", CLIENT)
                .claim("scope", AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_SCOPE)
                .claim("realm_access", Map.of("roles", List.of(AgentRuntimeWorkloadTokenPolicy.WORKLOAD_ROLE)))
                .build();

        assertRejected(missingJti, "missing-standard-claim");
    }

    private static Jwt token(
            String clientId,
            String authorizedParty,
            List<String> audiences,
            String scope,
            List<String> realmRoles,
            Map<String, Object> resourceAccess) {
        Jwt.Builder builder = baseToken()
                .audience(audiences)
                .claim("client_id", clientId)
                .claim("azp", authorizedParty)
                .claim("scope", scope)
                .claim("realm_access", Map.of("roles", realmRoles));
        if (resourceAccess != null) {
            builder.claim("resource_access", resourceAccess);
        }
        return builder.build();
    }

    private static Jwt.Builder baseToken() {
        return Jwt.withTokenValue("workload-token")
                .header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(SUBJECT)
                .issuedAt(Instant.parse("2026-07-20T10:00:00Z"))
                .expiresAt(Instant.parse("2026-07-20T10:01:00Z"))
                .claim("jti", "workload-token-jti");
    }

    private void assertRejected(Jwt token, String code) {
        assertThatThrownBy(() -> policy.resolve(token))
                .isInstanceOf(InvalidRuntimeWorkloadTokenException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
